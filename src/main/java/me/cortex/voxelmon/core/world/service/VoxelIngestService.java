package me.cortex.voxelmon.core.world.service;

import it.unimi.dsi.fastutil.Pair;
import me.cortex.voxelmon.core.voxelization.VoxelizedSection;
import me.cortex.voxelmon.core.voxelization.WorldConversionFactory;
import me.cortex.voxelmon.core.world.WorldEngine;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.LightStorage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;

public class VoxelIngestService {
    private volatile boolean running = true;
    private final Thread[] workers;

    private final ConcurrentLinkedDeque<WorldChunk> ingestQueue = new ConcurrentLinkedDeque<>();
    private final Semaphore ingestCounter = new Semaphore(0);

    private final ConcurrentHashMap<Long, Pair<ChunkNibbleArray, ChunkNibbleArray>> captureLightMap = new ConcurrentHashMap<>(1000,0.75f, 7);

    private final WorldEngine world;
    public VoxelIngestService(WorldEngine world, int workers) {
        this.world = world;

        this.workers = new Thread[workers];
        for (int i = 0; i < workers; i++) {
            var worker = new Thread(this::ingestWorker);
            worker.setDaemon(false);
            worker.setName("Ingest service #" + i);
            worker.start();
            this.workers[i]  = worker;
        }
    }

    private void ingestWorker() {
        while (this.running) {
            this.ingestCounter.acquireUninterruptibly();
            if (!this.running) break;
            var chunk = this.ingestQueue.pop();
            int i = chunk.getBottomSectionCoord() - 1;
            for (var section : chunk.getSectionArray()) {
                i++;
                var lighting = this.captureLightMap.remove(ChunkSectionPos.from(chunk.getPos(), i).asLong());
                if (section.isEmpty()) {
                    this.world.insertUpdate(VoxelizedSection.createEmpty(chunk.getPos().x, i, chunk.getPos().z));
                } else {
                    VoxelizedSection csec = WorldConversionFactory.convert(
                            this.world.getMapper(),
                            section.getBlockStateContainer(),
                            section.getBiomeContainer(),
                            (x, y, z, state) -> {
                                if (lighting == null || ((lighting.first() != null && lighting.first().isUninitialized())&&(lighting.second()!=null&&lighting.second().isUninitialized()))) {
                                    return (byte) 0xFF;
                                } else {
                                    //Lighting is a piece of shit cause its done per face
                                    int block = lighting.first()!=null?Math.min(15,lighting.first().get(x, y, z)):0xF;
                                    int sky = lighting.second()!=null?Math.min(15,lighting.second().get(x, y, z)):0xF;
                                    if (block<state.getLuminance()) {
                                        block = state.getLuminance();
                                    }
                                    sky = 15-sky;//This is cause sky light is inverted which saves memory when saving empty sections
                                    return (byte) (sky|(block<<4));
                                }
                            },
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
        var lp = chunk.getWorld().getLightingProvider();
        var blockLight = lp.get(LightType.BLOCK);
        var skyLight = lp.get(LightType.SKY);
        int i = chunk.getBottomSectionCoord() - 1;
        for (var section : chunk.getSectionArray()) {
            i++;
            if (section == null) continue;
            if (section.isEmpty()) continue;
            var pos = ChunkSectionPos.from(chunk.getPos(), i);
            if (lp.getStatus(LightType.BLOCK, pos) == LightStorage.Status.EMPTY)
                continue;
            var bl = blockLight.getLightSection(pos);
            var sl = skyLight.getLightSection(pos);
            if (bl == null && sl == null) continue;
            bl = bl==null?null:bl.copy();
            sl = sl==null?null:sl.copy();
            this.captureLightMap.put(pos.asLong(), Pair.of(bl, sl));
        }
        this.ingestQueue.add(chunk);
        this.ingestCounter.release();
    }

    public int getTaskCount() {
        return this.ingestCounter.availablePermits();
    }

    public void shutdown() {
        boolean anyAlive = false;
        boolean allAlive = true;
        for (var worker : this.workers) {
            anyAlive |= worker.isAlive();
            allAlive &= worker.isAlive();
        }

        if (!anyAlive) {
            System.err.println("Ingest workers already dead on shutdown! this is very very bad, check log for errors from this thread");
            return;
        }
        if (!allAlive) {
            System.err.println("Some ingest workers already dead on shutdown! this is very very bad, check log for errors from this thread");
        }

        //Wait for the ingest to finish
        while (this.ingestCounter.availablePermits() != 0) {
            Thread.onSpinWait();
        }
        //Shutdown
        this.running = false;
        this.ingestCounter.release(1000);
        //Wait for thread to join
        try {
            for (var worker : this.workers) {
                worker.join();
            }
        } catch (InterruptedException e) {throw new RuntimeException(e);}
    }
}
