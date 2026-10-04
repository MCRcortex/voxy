package me.cortex.voxy.commonImpl;

import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.config.section.SectionStorage;
import me.cortex.voxy.common.thread.ServiceManager;
import me.cortex.voxy.common.thread.UnifiedServiceThreadPool;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.service.SectionSavingService;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.locks.StampedLock;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

//TODO: add thread access verification (I.E. only accessible on a single thread)
public abstract class VoxyInstance {
    private volatile boolean isRunning = true;
    private final Thread worldCleaner;
    public final BooleanSupplier savingServiceRateLimiter;//Can run if this returns true
    protected final UnifiedServiceThreadPool threadPool;
    protected final SectionSavingService savingService;
    protected final VoxelIngestService ingestService;

    private final StampedLock activeWorldLock = new StampedLock();
    //WorldIdentifier -> WorldEngine or CompletableFuture<WorldEngine>
    private final HashMap<WorldIdentifier, Object> activeWorlds = new HashMap<>();

    protected final ImportManager importManager;
    protected final WorldMigrator migrator;

    public VoxyInstance() {
        if (!this.shouldCreateInstance()) {
            throw new DontCreateInstance();
        }
        Logger.info("Initializing voxy instance");
        this.threadPool = new UnifiedServiceThreadPool();
        this.savingService = new SectionSavingService(this.getServiceManager());
        this.ingestService = new VoxelIngestService(this.getServiceManager());
        this.savingServiceRateLimiter = ()->this.savingService.getTaskCount()<1200;
        this.importManager = this.createImportManager();
        this.migrator = this.createMigrator();

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

    protected boolean shouldCreateInstance() {
        return true;
    }

    protected void setNumThreads(int threads) {
        if (threads<0) throw new IllegalArgumentException("Num threads <0");
        if (this.threadPool.setNumThreads(threads)) {
            Logger.info("Dedicated voxy thread pool size: " + threads);
        }
    }

    public void updateDedicatedThreads() {
        this.setNumThreads(3);
    }

    protected ImportManager createImportManager() {
        return new ImportManager();
    }
    protected WorldMigrator createMigrator() {return new WorldMigrator();}

    public ServiceManager getServiceManager() {
        return this.threadPool.serviceManager;
    }
    public UnifiedServiceThreadPool getThreadPool() {
        return this.threadPool;
    }
    public VoxelIngestService getIngestService() {
        return this.ingestService;
    }
    public ImportManager getImportManager() {
        return this.importManager;
    }

    public void addDebug(List<String> debug) {
        debug.add("MemoryBuffer, Count/Size (mb): " + MemoryBuffer.getCount() + "/" + (MemoryBuffer.getTotalSize()/1_000_000));
        //TODO: fixme, doing this.activeWorlds.values() is not thread safe
        int totalWorlds = 0;
        int loadingWorlds = 0;
        String activeWorldCounts = "";
        try {
            for (var entry : this.activeWorlds.values()) {
                if (entry == null)
                    continue;
                totalWorlds++;
                if (entry instanceof WorldEngine we) {
                    activeWorldCounts += we.getActiveSectionCount() + ", ";
                } else if (entry instanceof CompletableFuture<?> cf) {
                    loadingWorlds++;
                } else {
                    throw new IllegalStateException("Should not reach here");
                }
            }
        } catch (Throwable e) { }
        debug.add(String.format("W(L)/I/S/[AWSC]: %s(%s)/%s/%s/[%s]",  totalWorlds, loadingWorlds, this.ingestService.getTaskCount(), this.savingService.getTaskCount(), activeWorldCounts));
    }

    CompletableFuture<WorldEngine> getOrCreatePureLoadingFuture(WorldIdentifier identifier, boolean incrementRef) {
        var worldOrFuture = getOrCreateLoadingFuture(identifier, incrementRef, true);
        if (worldOrFuture == null)
            throw new IllegalStateException();

        if (worldOrFuture instanceof WorldEngine we)
            return CompletableFuture.completedFuture(we);

        //noinspection unchecked
        return ((CompletableFuture<WorldEngine>) worldOrFuture);
    }

    private static WorldEngine ConvertAndMarkEngineNullable(Object worldOrFuture, boolean incrementRef) {
        if (worldOrFuture == null) return null;
        WorldEngine world = null;
        if (worldOrFuture instanceof WorldEngine engine) {
            world = engine;
        } else if (worldOrFuture instanceof CompletableFuture<?> future) {
            //noinspection unchecked
            world = ((CompletableFuture<WorldEngine>) future).getNow(null);
        }

        //World was fully loaded already
        if (world != null) {
            world.markActive();
            if (incrementRef) world.acquireRef();
            return world;
        }
        return null;
    }

    private static WorldEngine incrementRefNonNull(WorldEngine e) {
        if (e == null) return null;
        if (!e.isLive()) return null;
        e.markActive();
        e.acquireRef();
        return e;
    }
    Object getOrCreateLoadingFuture(WorldIdentifier identifier, boolean incrementRef, boolean loadIfMissing) {
        if (!this.isRunning) {
            Logger.error("Tried getting world object on voxy instance but its not running");
            return null;
        }

        long stamp = this.activeWorldLock.readLock();
        try {
            var worldOrFuture = this.activeWorlds.get(identifier);
            var world = ConvertAndMarkEngineNullable(worldOrFuture, incrementRef);

            //World was loaded already
            if (world != null)
                return world;

            //World is loading
            if (worldOrFuture != null) {
                if (!incrementRef) return worldOrFuture;
                return ((CompletableFuture<WorldEngine>) worldOrFuture).thenAccept(VoxyInstance::incrementRefNonNull);
            }

            //We need to load the world
            if (!loadIfMissing)
                return null;

            long writeStamp = this.activeWorldLock.tryConvertToWriteLock(stamp);
            stamp = writeStamp==0?stamp:writeStamp;
        } finally {
            if (StampedLock.isReadLockStamp(stamp))
                this.activeWorldLock.unlockRead(stamp);
        }

        if (StampedLock.isReadLockStamp(stamp))//Was previously a freed read lock
            stamp = this.activeWorldLock.writeLock();

        try {
            //needs to be here for the final write unlock
            if (!this.isRunning) {
                Logger.error("Tried getting world object on voxy instance but its not running");
                //This is like a worst case panik situation
                return CompletableFuture.completedFuture(null);
            }

            var worldOrFuture = this.activeWorlds.get(identifier);
            var world = ConvertAndMarkEngineNullable(worldOrFuture, incrementRef);
            //World was loaded already
            if (world != null)
                return world;
            //World is loading/had a future
            if (worldOrFuture != null) {
                if (!incrementRef) return worldOrFuture;
                return ((CompletableFuture<WorldEngine>) worldOrFuture).thenAccept(VoxyInstance::incrementRefNonNull);
            }
            //actually create the world or future
            worldOrFuture = this.createWorldImmediateOrFuture(identifier);
            if (worldOrFuture == null) {
                throw new IllegalStateException("createWorldImmediateOrFuture should never return null");
            }
            world = ConvertAndMarkEngineNullable(worldOrFuture, incrementRef);
            if (world != null) {
                worldOrFuture = world;
            } else {
                //Future insertion logic
                worldOrFuture = ((CompletableFuture<WorldEngine>) worldOrFuture).exceptionally(e->{
                    Logger.error("Async world loading had an error while loading: ", e);
                    return null;
                }).thenApply(engine -> {
                    long writeStamp = this.activeWorldLock.writeLock();
                    try {
                        if (!this.isRunning) {
                            //Its not an error, since we are (presumably) in shutdown
                            if (engine != null) engine.free();
                            var prev = this.activeWorlds.remove(identifier);
                            if (!(prev instanceof CompletableFuture<?>))
                                throw new IllegalStateException("State not what was expected during shutdown");
                            return null;
                        }
                        if (engine != null) engine.markActive();//To refresh the time before lock

                        var gotValue = this.activeWorlds.getOrDefault(identifier, SENTINAL_DEFAULT);
                        if (!(gotValue instanceof CompletableFuture<?>)) {
                            var msg = "Unexpected state in the activeWorld map for world " + identifier + " of " + gotValue;
                            Logger.error(msg);
                            throw new IllegalStateException(msg);
                        }
                        if (engine == null) {
                            //This should never happen normally
                            Logger.error("Completable world loading future returned null, this is really really bad");
                            this.activeWorlds.remove(identifier);//we must remove it
                            throw new IllegalStateException();
                        }
                        engine.markActive();
                        if (incrementRef) engine.acquireRef();

                        //We need to set/update/replace the future in the activeWorlds map first, which requires a lock
                        this.activeWorlds.put(identifier, engine);
                        return engine;
                    } finally {
                        this.activeWorldLock.unlockWrite(writeStamp);
                    }
                });
            }
            //Insert it
            this.activeWorlds.put(identifier, worldOrFuture);
            return worldOrFuture;
        } finally {
            this.activeWorldLock.unlockWrite(stamp);
        }
    }


    protected abstract SectionStorage createStorage(WorldIdentifier identifier);

    //Immediately creates the world, or a future if its a long loading
    private Object createWorldImmediateOrFuture(WorldIdentifier id) {
        Function<WorldIdentifier, WorldEngine> actualLoader = (identifier)->{
            Logger.info("Creating new world engine: " + identifier.getLongHash() + "@" + System.identityHashCode(this));
            var world = new WorldEngine(this.createStorage(identifier), this);
            world.setSaveCallback(this.savingService::enqueueSave);
            return world;
        };
        var migrationFuture = this.migrator.createMigrationFutureIfNeeded(id ,actualLoader);
        if (migrationFuture != null) {
            Logger.info("Created a migration future for world: " + id);
            return migrationFuture;
        }
        //No migration, create it immediatly
        return actualLoader.apply(id);
    }


    private static final Object SENTINAL_DEFAULT = new Object();
    protected void cleanIdle() {
        List<WorldIdentifier> worldsToCheck = new ArrayList<>(0);
        {
            long stamp = this.activeWorldLock.readLock();
            try {
                for (var pair : this.activeWorlds.entrySet()) {
                    var worldOrFuture = pair.getValue();
                    if (worldOrFuture == null) worldsToCheck.add(pair.getKey());//Unsure how this happens but just in case
                    if (worldOrFuture instanceof WorldEngine world) {
                        if (world.isWorldIdle())
                            worldsToCheck.add(pair.getKey());
                    } else if (worldOrFuture instanceof CompletableFuture<?> future) {
                        if (future.isDone()) worldsToCheck.add(pair.getKey());
                        //The world is loading so we just ignore it for now
                    } else {
                        throw new IllegalStateException();
                    }
                }
            } finally {
                this.activeWorldLock.unlockRead(stamp);
            }
        }
        if (worldsToCheck.isEmpty()) return;
        //Shutdown and clear all idle worlds
        long stamp = this.activeWorldLock.writeLock();
        try {
            for (var id : worldsToCheck) {
                var worldOrFuture = this.activeWorlds.getOrDefault(id, SENTINAL_DEFAULT);
                if (worldOrFuture == SENTINAL_DEFAULT) continue;//Not even in the map
                if (worldOrFuture == null) {
                    Logger.error("Id had a null enty: " + id);
                    //Race condition between unlock read and acquire write or other, either way, remove it
                    this.activeWorlds.remove(id);
                    continue;
                }
                if (worldOrFuture instanceof CompletableFuture<?> future) {
                    if (future.isCancelled()) {
                        //if its canceled then just remove it
                        this.activeWorlds.remove(id);
                        continue;
                    }
                    if (!future.isDone()) {
                        continue;//its not done yet
                    }
                    if (future.isDone()) {
                        Logger.warn("Loading future has finished but is still in the map, keeping it");
                        continue;
                    }
                }
                if (!(worldOrFuture instanceof WorldEngine world)) throw new IllegalStateException("No idea what this is: " + worldOrFuture);
                if (!world.isWorldIdle()) {//Between then and now its not idle
                    this.activeWorlds.put(id, world);
                    continue;
                }
                Logger.info("Shutting down idle world: " + id.getLongHash());
                //If is here close and free the world
                world.free();
                this.activeWorlds.remove(id);
            }
        } finally {
            this.activeWorldLock.unlockWrite(stamp);
        }
    }

    public void shutdown() {
        Logger.info("Shutting down voxy instance");
        this.isRunning = false;
        try {
            this.worldCleaner.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        //Force clean idle
        this.cleanIdle();

        //Cancel imports
        if (!this.activeWorlds.isEmpty()) {
            long stamp = this.activeWorldLock.readLock();
            try {
                for (var worldOrFuture : this.activeWorlds.values()) {
                    if (worldOrFuture instanceof WorldEngine engine)
                        this.importManager.cancelImport(engine);
                }
            } finally {
                this.activeWorldLock.unlockRead(stamp);
            }
        }

        //Stop ingest and saving
        try {this.ingestService.shutdown();} catch (Exception e) {Logger.error(e);}
        try {this.savingService.shutdown();} catch (Exception e) {Logger.error(e);}

        //We stop migration here, all futures should be finished or completed exceptionally
        try {this.migrator.shutdown();} catch (Exception e) {Logger.error(e);}

        //Clean again
        this.cleanIdle();

        long stamp = this.activeWorldLock.writeLock();
        if (!this.activeWorlds.isEmpty()) {
            boolean printedNotice = false;
            for (var worldOrFuture : new ArrayList<>(this.activeWorlds.values())) {
                if (worldOrFuture == null) {
                    Logger.error("value was null when shutting down");
                    continue;
                }
                if (worldOrFuture instanceof WorldEngine world) {
                    if (world.isWorldUsed()) {
                        if (!printedNotice) {
                            printedNotice = true;
                            Logger.error("Not all worlds shutdown, force closing worlds");
                        }
                        //Dont lock in the loopy thing, this should basicly never happen if it does something horrific happened
                        this.activeWorldLock.unlockWrite(stamp);
                        while (world.isWorldUsed()) {
                            try {
                                //noinspection BusyWait
                                Thread.sleep(10);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        stamp = this.activeWorldLock.writeLock();
                    }
                    //Free the world
                    world.free();
                } else if (worldOrFuture instanceof CompletableFuture<?> future) {
                    if (future.isDone()) {
                        Logger.warn("Future wasnt finished but now is");
                        continue;
                    } else {
                        Logger.warn("Future not finished, blocking until finished");
                        //we need to unlock join and relock
                        this.activeWorldLock.unlockWrite(stamp);
                        future.join();
                        stamp = this.activeWorldLock.writeLock();
                    }
                } else {
                    throw new IllegalStateException("What is this: " + worldOrFuture);
                }
            }
        }

        try {this.threadPool.shutdown();} catch (Exception e) {Logger.error(e);}
        Logger.info("Instance shutdown");
        this.activeWorldLock.unlockWrite(stamp);
        this.cleanIdle();
        if (!this.activeWorlds.isEmpty()) {
            throw new IllegalStateException("Not all worlds shutdown");
        }
    }

    public boolean isIngestEnabled(WorldIdentifier worldId) {
        return true;
    }

    public boolean isRunning() {
        return this.isRunning;
    }
}