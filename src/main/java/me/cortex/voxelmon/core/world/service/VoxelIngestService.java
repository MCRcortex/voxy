package me.cortex.voxelmon.core.world.service;

import me.cortex.voxelmon.core.voxelization.VoxelizedSection;
import me.cortex.voxelmon.core.voxelization.WorldConversionFactory;
import me.cortex.voxelmon.core.world.WorldEngine;
import net.minecraft.world.chunk.WorldChunk;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;

public class VoxelIngestService {
    private volatile boolean running = true;
    private final Thread worker;

    private final ConcurrentLinkedDeque<WorldChunk> ingestQueue = new ConcurrentLinkedDeque<>();
    private final Semaphore ingestCounter = new Semaphore(0);

    private final WorldEngine world;
    public VoxelIngestService(WorldEngine world) {
        this.worker = new Thread(this::ingestWorker);
        this.worker.setDaemon(false);
        this.worker.setName("Ingest service");
        this.worker.start();

        this.world = world;
    }

    private void ingestWorker() {
        while (this.running) {
            this.ingestCounter.acquireUninterruptibly();
            if (!this.running) break;
            var chunk = this.ingestQueue.pop();
            int i = chunk.getBottomSectionCoord() - 1;
            for (var section : chunk.getSectionArray()) {
                i++;
                if (section.isEmpty()) {
                    this.world.insertUpdate(VoxelizedSection.createEmpty(chunk.getPos().x, i, chunk.getPos().z));
                } else {
                    VoxelizedSection csec = WorldConversionFactory.convert(
                            this.world.getMapper(),
                            section.getBlockStateContainer(),
                            section.getBiomeContainer(),
                            (x, y, z) -> (byte) 0,
                            chunk.getPos().x,
                            i,
                            chunk.getPos().z
                    );

                    this.world.insertUpdate(csec);
                }
            }
        }
    }

    public void enqueueIngest(WorldChunk chunk) {
        this.ingestQueue.add(chunk);
        this.ingestCounter.release();
    }

    public int getTaskCount() {
        return this.ingestCounter.availablePermits();
    }

    public void shutdown() {
        if (!this.worker.isAlive()) {
            System.err.println("Ingest worker already dead on shutdown! this is very very bad, check log for errors from this thread");
            return;
        }

        //Wait for the ingest to finish
        while (this.ingestCounter.availablePermits() != 0) {
            Thread.onSpinWait();
        }
        //Shutdown
        this.running = false;
        this.ingestCounter.release(1000);
        //Wait for thread to join
        try {this.worker.join();} catch (InterruptedException e) {throw new RuntimeException(e);}
    }
}
