package me.cortex.neovoxy.client;

import me.cortex.neovoxy.client.core.gl.Capabilities;
import me.cortex.neovoxy.client.core.model.bakery.BudgetBufferRenderer;
import me.cortex.neovoxy.client.core.rendering.util.SharedIndexBuffer;
import me.cortex.neovoxy.client.config.NeoVoxyConfig;
import me.cortex.neovoxy.common.Logger;
import me.cortex.neovoxy.common.world.service.VoxelIngestService;
import me.cortex.neovoxy.commonImpl.NeoVoxyCommon;
import me.cortex.neovoxy.commonImpl.WorldIdentifier;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = "neovoxy", value = Dist.CLIENT)
public class NeoVoxyClient {
    private static final HashSet<String> FREX = new HashSet<>();
    private static final Set<LevelChunk> PENDING_CHUNK_INGEST = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<Level, Set<Long>> PENDING_SECTION_INGEST = new IdentityHashMap<>();

    public static void initNeoVoxyClient() {
        Capabilities.init();//Ensure clinit is called

        if (Capabilities.INSTANCE.hasBrokenDepthSampler) {
            Logger.error("AMD broken depth sampler detected, neovoxy does not work correctly and has been disabled, this will hopefully be fixed in the future");
        }

        boolean systemSupported = Capabilities.INSTANCE.compute && Capabilities.INSTANCE.indirectParameters && !Capabilities.INSTANCE.hasBrokenDepthSampler;
        if (systemSupported) {

            SharedIndexBuffer.INSTANCE.id();
            BudgetBufferRenderer.init();

            NeoVoxyCommon.setInstanceFactory(NeoVoxyClientInstance::new);

            if (!Capabilities.INSTANCE.subgroup) {
                Logger.warn("GPU does not support subgroup operations, expect some performance degradation");
            }

        } else {
            Logger.error("NeoVoxy is unsupported on your system.");
        }
    }

    @SubscribeEvent
    public static void registerCommands(RegisterClientCommandsEvent event) {
        if (NeoVoxyCommon.isAvailable()) {
            event.getDispatcher().register(NeoVoxyCommands.register());
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        if (event.getLevel().isClientSide() && NeoVoxyConfig.CONFIG.ingestEnabled) {
            PENDING_CHUNK_INGEST.add(chunk);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        if (event.getLevel().isClientSide()) {
            PENDING_CHUNK_INGEST.remove(chunk);
            if (NeoVoxyConfig.CONFIG.ingestEnabled) {
                VoxelIngestService.tryAutoIngestChunk(chunk);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level && level.isClientSide()) {
            PENDING_CHUNK_INGEST.removeIf(chunk -> chunk.getLevel() == level);
            PENDING_SECTION_INGEST.remove(level);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!NeoVoxyConfig.CONFIG.ingestEnabled) {
            PENDING_CHUNK_INGEST.clear();
            PENDING_SECTION_INGEST.clear();
            return;
        }

        PENDING_CHUNK_INGEST.removeIf(VoxelIngestService::tryAutoIngestChunk);
        PENDING_SECTION_INGEST.forEach(NeoVoxyClient::ingestSections);
        PENDING_SECTION_INGEST.clear();
    }

    public static void queueSectionIngest(Level level, int sectionX, int sectionY, int sectionZ) {
        if (NeoVoxyConfig.CONFIG.ingestEnabled) {
            PENDING_SECTION_INGEST
                    .computeIfAbsent(level, ignored -> new HashSet<>())
                    .add(SectionPos.asLong(sectionX, sectionY, sectionZ));
        }
    }

    private static void ingestSections(Level level, Set<Long> positions) {
        var world = WorldIdentifier.of(level);
        if (world == null) {
            return;
        }

        for (long packedPos : positions) {
            int sectionX = SectionPos.x(packedPos);
            int sectionY = SectionPos.y(packedPos);
            int sectionZ = SectionPos.z(packedPos);
            var chunk = level.getChunk(sectionX, sectionZ, ChunkStatus.FULL, false);
            if (chunk == null) {
                continue;
            }

            int sectionIndex = sectionY - level.getMinSection();
            if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) {
                continue;
            }

            var sectionPos = SectionPos.of(sectionX, sectionY, sectionZ);
            var lightEngine = level.getLightEngine();
            var blockLight = lightEngine.getLayerListener(LightLayer.BLOCK).getDataLayerData(sectionPos);
            var skyLight = lightEngine.getLayerListener(LightLayer.SKY).getDataLayerData(sectionPos);
            VoxelIngestService.rawIngest(
                    world,
                    chunk.getSection(sectionIndex),
                    sectionX,
                    sectionY,
                    sectionZ,
                    blockLight == null ? null : blockLight.copy(),
                    skyLight == null ? null : skyLight.copy()
            );
        }
    }

    public static boolean isFrexActive() {
        return !FREX.isEmpty();
    }

    public static int getOcclusionDebugState() {
        return 0;
    }

    public static boolean disableSodiumChunkRender() {
        return false;// getOcclusionDebugState() != 0;
    }
}
