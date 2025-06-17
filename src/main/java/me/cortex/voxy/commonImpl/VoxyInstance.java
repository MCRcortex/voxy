package me.cortex.voxy.commonImpl;

import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.config.section.SectionStorage;
import me.cortex.voxy.common.thread.ServiceThreadPool;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.service.SectionSavingService;
import me.cortex.voxy.common.world.service.VoxelIngestService;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.locks.StampedLock;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

//TODO: add thread access verification (I.E. only accessible on a single thread)
public abstract class VoxyInstance {
    private volatile boolean isRunning = true;
    private final Thread worldCleaner;
    public final BooleanSupplier savingServiceRateLimiter;//Can run if this returns true
    protected final ServiceThreadPool threadPool;
    protected final SectionSavingService savingService;
    protected final VoxelIngestService ingestService;

    private final StampedLock activeWorldLock = new StampedLock();
    private final HashMap<WorldIdentifier, WorldEngine> activeWorlds = new HashMap<>();

    protected final ImportManager importManager;

    public VoxyInstance(int threadCount) {
        Logger.info("Initializing voxy instance");
        this.threadPool = new ServiceThreadPool(threadCount);
        this.savingService = new SectionSavingService(this.threadPool);
        this.ingestService = new VoxelIngestService(this.threadPool);
        this.importManager = this.createImportManager();
        this.savingServiceRateLimiter = ()->this.savingService.getTaskCount()<1200;
        this.worldCleaner = new Thread(()->{
            try {
                while (this.isRunning) {
                    //noinspection BusyWait
                    Thread.sleep(1000);
                    this.cleanIdle();
                }
            } catch (InterruptedException e) {
                //We are exiting, so just exit
            } catch (Exception e) {
                Logger.error("Exception in world cleaner",e);
            }
        });
        this.worldCleaner.setPriority(Thread.MIN_PRIORITY);
        this.worldCleaner.setName("Active world cleaner");
        this.worldCleaner.setDaemon(true);
        this.worldCleaner.start();
    }

    protected ImportManager createImportManager() {
        return new ImportManager();
    }

    public ServiceThreadPool getThreadPool() {
        return this.threadPool;
    }
    public VoxelIngestService getIngestService() {
        return this.ingestService;
    }
    public ImportManager getImportManager() {
        return this.importManager;
    }

    //TODO: reference count the world object
    // have automatic world cleanup after ~1 minute of inactivity and the reference count equaling zero possibly
    // note, the reference count should be separate from the number of active chunks to prevent many issues
    // a world is no longer active once it has no reference counts and no active chunks associated with it
    public WorldEngine getNullable(WorldIdentifier identifier) {
        var cache = identifier.cachedEngineObject;
        WorldEngine world;
        if (cache == null) {
            world = null;
        } else {
            world = cache.get();
            if (world == null) {
                identifier.cachedEngineObject = null;
            } else {
                if (world.isLive()) {
                    if (world.instanceIn != this) {
                        throw new IllegalStateException("World cannot be in identifier cache, alive and not part of this instance");
                    }
                    //Successful cache hit
                } else {
                    identifier.cachedEngineObject = null;
                    world = null;
                }
            }
        }
        if (world == null) {//If the cached world is null, try get from the active worlds
            long stamp = this.activeWorldLock.readLock();
            world = this.activeWorlds.get(identifier);
            this.activeWorldLock.unlockRead(stamp);
            if (world != null) {//Setup cache
                identifier.cachedEngineObject = new WeakReference<>(world);
            }
        }
        if (world != null) {
            //Mark the world as active
            world.markActive();
        }
        return world;
    }

    public WorldEngine getOrCreate(WorldIdentifier identifier) {
        var world = this.getNullable(identifier);
        if (world != null) {
            return world;
        }
        long stamp = this.activeWorldLock.writeLock();
        world = this.activeWorlds.get(identifier);
        if (world == null) {
            //Create world here
            world = this.createWorld(identifier);
        }
        this.activeWorldLock.unlockWrite(stamp);
        identifier.cachedEngineObject = new WeakReference<>(world);
        return world;
    }


    protected abstract SectionStorage createStorage(WorldIdentifier identifier);

    private WorldEngine createWorld(WorldIdentifier identifier) {
        if (this.activeWorlds.containsKey(identifier)) {
            throw new IllegalStateException("Existing world with identifier");
        }
        Logger.info("Creating new world engine: " + identifier.getLongHash());
        var world = new WorldEngine(this.createStorage(identifier), this);
        world.setSaveCallback(this.savingService::enqueueSave);
        this.activeWorlds.put(identifier, world);
        return world;
    }

    public void cleanIdle() {
        List<WorldIdentifier> idleWorlds = null;
        {
            long stamp = this.activeWorldLock.readLock();
            for (var pair : this.activeWorlds.entrySet()) {
                if (pair.getValue().isWorldIdle()) {
                    if (idleWorlds == null) idleWorlds = new ArrayList<>();
                    idleWorlds.add(pair.getKey());
                }
            }
            this.activeWorldLock.unlockRead(stamp);
        }

        if (idleWorlds != null) {
            //Shutdown and clear all idle worlds
            long stamp = this.activeWorldLock.writeLock();
            for (var id : idleWorlds) {
                var world = this.activeWorlds.remove(id);
                if (world == null) continue;//Race condition between unlock read and acquire write
                if (!world.isWorldIdle()) {this.activeWorlds.put(id, world); continue;}//No longer idle
                Logger.info("Shutting down idle world: " + id.getLongHash());
                //If is here close and free the world
                world.free();
            }
            this.activeWorldLock.unlockWrite(stamp);
        }
    }

    public void addDebug(List<String> debug) {
        debug.add("Voxy Core: " + VoxyCommon.MOD_VERSION);
        debug.add("MemoryBuffer, Count/Size (mb): " + MemoryBuffer.getCount() + "/" + (MemoryBuffer.getTotalSize()/1_000_000));
        debug.add("I/S/AWSC: " + this.ingestService.getTaskCount() + "/" + this.savingService.getTaskCount() + "/[" + this.activeWorlds.values().stream().map(a->""+a.getActiveSectionCount()).collect(Collectors.joining(", ")) + "]");//Active world section count
    }

    public void shutdown() {
        Logger.info("Shutting down voxy instance");
        this.isRunning = false;
        try {
            this.worldCleaner.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        this.cleanIdle();

        if (!this.activeWorlds.isEmpty()) {
            long stamp = this.activeWorldLock.readLock();
            for (var world : this.activeWorlds.values()) {
                this.importManager.cancelImport(world);
            }
            this.activeWorldLock.unlockRead(stamp);
        }

        try {this.ingestService.shutdown();} catch (Exception e) {Logger.error(e);}
        try {this.savingService.shutdown();} catch (Exception e) {Logger.error(e);}


        long stamp = this.activeWorldLock.writeLock();

        if (!this.activeWorlds.isEmpty()) {
            boolean printedNotice = false;
            for (var world : this.activeWorlds.values()) {
                if (world.isWorldUsed()) {
                    if (!printedNotice) {
                        printedNotice = true;
                        Logger.error("Not all worlds shutdown, force closing worlds");
                    }
                    while (world.isWorldUsed()) {
                        try {
                            //noinspection BusyWait
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                //Free the world
                world.free();
            }
            this.activeWorlds.clear();
        }

        try {this.threadPool.shutdown();} catch (Exception e) {Logger.error(e);}

        if (!this.activeWorlds.isEmpty()) {
            throw new IllegalStateException("Not all worlds shutdown");
        }
        Logger.info("Instance shutdown");
        this.activeWorldLock.unlockWrite(stamp);
    }
}