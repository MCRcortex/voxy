package me.cortex.voxy.common.world.other;

import ca.spottedleaf.starlight.common.light.StarLightEngine;
import ca.spottedleaf.starlight.common.light.StarLightInterface;
import ca.spottedleaf.starlight.common.light.StarLightLightingProvider;
import it.unimi.dsi.fastutil.Pair;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Map;

public class LightingFetcher {
    //TODO: FIXME: I dont think the 2 codepaths are needed, just do what you do for starlight for vanilla and it should work just fine

    private static boolean STARLIGHT_INSTALLED = FabricLoader.getInstance().isModLoaded("starlight");
    private static void fetchLightingDataVanilla(Map<Long, Pair<ChunkNibbleArray, ChunkNibbleArray>> out, WorldChunk chunk) {
        var lp = chunk.getWorld().getLightingProvider();
        var blockLight = lp.get(LightType.BLOCK);
        var skyLight = lp.get(LightType.SKY);
        int i = chunk.getBottomSectionCoord() - 1;
        for (var section : chunk.getSectionArray()) {
            i++;
            if (section == null) continue;
            if (section.isEmpty()) continue;
            var pos = ChunkSectionPos.from(chunk.getPos(), i);
            if (blockLight.getLightSection(pos).isUninitialized())
                continue;
            var bl = blockLight.getLightSection(pos);
            var sl = skyLight.getLightSection(pos);
            if (bl == null && sl == null) continue;
            bl = bl==null?null:bl.copy();
            sl = sl==null?null:sl.copy();
            out.put(pos.asLong(), Pair.of(bl, sl));
        }
    }

    private static void fetchLightingDataStarlight(Map<Long, Pair<ChunkNibbleArray, ChunkNibbleArray>> out, WorldChunk chunk) {
        var starlight = ((StarLightLightingProvider)chunk.getWorld().getLightingProvider());
        var blp = starlight.getLightEngine().getBlockReader();
        var slp = starlight.getLightEngine().getSkyReader();

        int i = chunk.getBottomSectionCoord() - 1;
        for (var section : chunk.getSectionArray()) {
            i++;
            if (section == null) continue;
            if (section.isEmpty()) continue;
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

    public static void fetchLightingData(Map<Long, Pair<ChunkNibbleArray, ChunkNibbleArray>> out, WorldChunk chunk) {
        if (STARLIGHT_INSTALLED) {
            fetchLightingDataStarlight(out, chunk);
        } else {
            fetchLightingDataVanilla(out, chunk);
        }
    }
}
