package me.cortex.voxy.common.world.service;

import it.unimi.dsi.fastutil.Pair;
import me.cortex.voxy.client.Voxy;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.voxelization.WorldConversionFactory;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.thread.ServiceSlice;
import me.cortex.voxy.common.world.thread.ServiceThreadPool;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.LightStorage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;

public class VoxelIngestService {
    private static final ThreadLocal<VoxelizedSection> SECTION_CACHE = ThreadLocal.withInitial(VoxelizedSection::createEmpty);
    private final ServiceSlice threads;
    private final ConcurrentLinkedDeque<WorldChunk> ingestQueue = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap<Long, Pair<ChunkNibbleArray, ChunkNibbleArray>> captureLightMap = new ConcurrentHashMap<>(1000,0.75f, 7);

    private final WorldEngine world;
    public VoxelIngestService(WorldEngine world, ServiceThreadPool pool) {
        this.world = world;
        this.threads = pool.createService("Ingest service", 100, ()-> this::processJob);
    }

    private void processJob() {
        var chunk = this.ingestQueue.pop();
        int i = chunk.getBottomSectionCoord() - 1;
        for (var section : chunk.getSectionArray()) {
            i++;
            var lighting = this.captureLightMap.remove(ChunkSectionPos.from(chunk.getPos(), i).asLong());
            if (section.isEmpty() && lighting==null) {//If the chunk section has lighting data, propagate it
                //TODO: add local cache so that it doesnt constantly create new sections
                this.world.insertUpdate(VoxelizedSection.createEmpty().setPosition(chunk.getPos().x, i, chunk.getPos().z));
            } else {
                VoxelizedSection csec = WorldConversionFactory.convert(
                        SECTION_CACHE.get().setPosition(chunk.getPos().x, i, chunk.getPos().z),
                        this.world.getMapper(),
                        section.getBlockStateContainer(),
                        section.getBiomeContainer(),
                        (x, y, z, state) -> {
                            if (lighting == null || ((lighting.first() != null && lighting.first().isUninitialized())&&(lighting.second()!=null&&lighting.second().isUninitialized()))) {
                                return (byte) 0;
                            } else {
                                //Lighting is hell
                                int block = lighting.first()!=null?Math.min(15,lighting.first().get(x, y, z)):0;
                                int sky = lighting.second()!=null?Math.min(15,lighting.second().get(x, y, z)):0;
                                if (block<state.getLuminance()) {
                                    block = state.getLuminance();
                                }
                                return (byte) (sky|(block<<4));
                            }
                        }
                );
                WorldConversionFactory.mipSection(csec, this.world.getMapper());
                this.world.insertUpdate(csec);
            }
        }
    }

    private static void fetchLightingData(Map<Long, Pair<ChunkNibbleArray, ChunkNibbleArray>> out, WorldChunk chunk) {
        var lightingProvider = chunk.getWorld().getLightingProvider();
        var blp = lightingProvider.get(LightType.BLOCK);
        var slp = lightingProvider.get(LightType.SKY);

        int i = chunk.getBottomSectionCoord() - 1;
        for (var section : chunk.getSectionArray()) {
            i++;
            if (section == null) continue;
            //if (section.isEmpty()) continue;
            var pos = ChunkSectionPos.from(chunk.getPos(), i);
            var bl = blp.getLightSection(pos);
            if (!(bl == null || bl.isUninitialized())) {
                bl = bl.copy();
            } else {
                bl = null;
            }
            var sl = slp.getLightSection(pos);
            if (!(sl == null || sl.isUninitialized())) {
                sl = sl.copy();
            } else {
                sl = null;
            }
            if (bl == null && sl == null) {
                continue;
            }
            out.put(pos.asLong(), Pair.of(bl, sl));
        }
    }

    public void enqueueIngest(WorldChunk chunk) {
        fetchLightingData(this.captureLightMap, chunk);
        this.ingestQueue.add(chunk);
        this.threads.execute();
    }

    public int getTaskCount() {
        return this.threads.getJobCount();
    }

    public void shutdown() {
        this.threads.shutdown();
    }
}
