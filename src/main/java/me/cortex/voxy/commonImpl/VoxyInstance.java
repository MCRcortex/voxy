package me.cortex.voxy.commonImpl;

import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.config.section.SectionStorage;
import me.cortex.voxy.common.thread.ServiceThreadPool;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.service.SectionSavingService;
import me.cortex.voxy.common.world.service.VoxelIngestService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

//TODO: add thread access verification (I.E. only accessible on a single thread)
public class VoxyInstance {
    protected final ServiceThreadPool threadPool;
    protected final SectionSavingService savingService;
    protected final VoxelIngestService ingestService;
    protected final Set<WorldEngine> activeWorlds = new HashSet<>();

    protected final ImportManager importManager;

    public VoxyInstance(int threadCount) {
        Logger.info("Initializing voxy instance");
        this.threadPool = new ServiceThreadPool(threadCount);
        this.savingService = new SectionSavingService(this.threadPool);
        this.ingestService = new VoxelIngestService(this.threadPool);
        this.importManager = this.createImportManager();
    }

    protected ImportManager createImportManager() {
        return new ImportManager();
    }

    public void addDebug(List<String> debug) {
        debug.add("Voxy Core: " + VoxyCommon.MOD_VERSION);
        debug.add("MemoryBuffer, Count/Size (mb): " + MemoryBuffer.getCount() + "/" + (MemoryBuffer.getTotalSize()/1_000_000));
        debug.add("I/S/AWSC: " + this.ingestService.getTaskCount() + "/" + this.savingService.getTaskCount() + "/[" + this.activeWorlds.stream().map(a->""+a.getActiveSectionCount()).collect(Collectors.joining(", ")) + "]");//Active world section count
    }

    public void shutdown() {
        Logger.info("Shutdown voxy instance");

        if (!this.activeWorlds.isEmpty()) {
            for (var world : this.activeWorlds) {
                this.importManager.cancelImport(world);
            }
        }

        try {this.ingestService.shutdown();} catch (Exception e) {Logger.error(e);}
        try {this.savingService.shutdown();} catch (Exception e) {Logger.error(e);}

        if (!this.activeWorlds.isEmpty()) {
            Logger.error("Not all worlds shutdown, force closing " + this.activeWorlds.size() + " worlds");
            for (var world : new HashSet<>(this.activeWorlds)) {//Create a clone
                this.stopWorld(world);
            }
        }

        try {this.threadPool.shutdown();} catch (Exception e) {Logger.error(e);}

        if (!this.activeWorlds.isEmpty()) {
            throw new IllegalStateException("Not all worlds shutdown");
        }
        Logger.info("Instance shutdown");
    }

    public ServiceThreadPool getThreadPool() {
        return this.threadPool;
    }

    public VoxelIngestService getIngestService() {
        return this.ingestService;
    }

    public SectionSavingService getSavingService() {
        return this.savingService;
    }

    public ImportManager getImportManager() {
        return this.importManager;
    }

    public void flush() {
        try {
            while (this.ingestService.getTaskCount() != 0) {
                Thread.sleep(10);
            }
            while (this.savingService.getTaskCount() != 0) {
                Thread.sleep(10);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    protected WorldEngine createWorld(SectionStorage storage) {
        var world = new WorldEngine(storage, this);
        world.setSaveCallback(this.savingService::enqueueSave);
        this.activeWorlds.add(world);
        return world;
    }

    //There are 4 possible "states" for world selection/management
    // 1) dedicated server
    // 2) client singleplayer
    // 3) client singleplayer as lan host (so also a server)
    // 4) client multiplayer (remote server)

    //The thing with singleplayer is that it is more efficent to make it bound to clientworld (think)
    // so if make into singleplayer as host, would need to reload the system into that mode
    // so that the world renderer uses the WorldEngine of the server

    public void stopWorld(WorldEngine world) {
        if (!this.activeWorlds.contains(world)) {
            if (world.isLive()) {
                throw new IllegalStateException("World cannot be live and not in world set");
            }
            throw new IllegalStateException("Cannot close world which is not part of instance");
        }
        if (!world.isLive()) {
            throw new IllegalStateException("World cannot be in world set and not alive");
        }

        this.importManager.cancelImport(world);

        if (world.getActiveSectionCount() != 0) {
            Logger.warn("Waiting for world to finish use");
            while (world.getActiveSectionCount() != 0) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        //TODO: maybe replace the flush with an atomic "in queue" counter that is per world
        this.flush();

        world.free();
        this.activeWorlds.remove(world);
    }
}