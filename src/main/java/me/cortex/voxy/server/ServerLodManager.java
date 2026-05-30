package me.cortex.voxy.server;

import me.cortex.voxy.VoxyMod;
import me.cortex.voxy.common.config.VoxyConfig;
import me.cortex.voxy.common.lod.LodSection;
import me.cortex.voxy.common.lod.LodMipper;
import me.cortex.voxy.common.lod.SectionKey;
import me.cortex.voxy.common.lod.WorldManifest;
import me.cortex.voxy.common.storage.SectionStorageManager;
import me.cortex.voxy.common.storage.SqliteSectionStorage;
import me.cortex.voxy.common.voxelization.ChunkVoxelizer;
import me.cortex.voxy.network.payload.LodSectionDataPayload;
import me.cortex.voxy.network.payload.SectionRemovePayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Central server-side LOD manager.
 *
 * <ul>
 *   <li>Listens for chunk load events and voxelizes new/changed chunks.
 *   <li>Stores LOD sections per dimension in {@link SectionStorageManager}.
 *   <li>Generates higher LOD levels (1–N) by mipping.
 *   <li>Pushes live section updates to all connected clients.
 * </ul>
 *
 * <p>One instance exists per logical server lifetime, created in {@link VoxyServer}.
 */
public final class ServerLodManager implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerLodManager.class);

    private final MinecraftServer server;
    private final SectionStorageManager storageManager;
    private final ExecutorService voxelizerPool;

    /** Pending voxelization tasks submitted from the server thread. */
    private final ConcurrentLinkedQueue<LevelChunk> pendingChunks = new ConcurrentLinkedQueue<>();

    public ServerLodManager(MinecraftServer server) {
        this.server = server;
        Path worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("voxy");
        this.storageManager = new SectionStorageManager(worldDir);

        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        this.voxelizerPool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "voxy-voxelizer");
            t.setDaemon(true);
            return t;
        });

        LOGGER.info("[Voxy] Server LOD manager started (voxelizer threads: {})", threads);
    }

    // -------------------------------------------------------------------------
    // Chunk events
    // -------------------------------------------------------------------------

    /** Called when a chunk is fully loaded on the server. */
    public void onChunkLoaded(ServerLevel level, LevelChunk chunk) {
        pendingChunks.add(chunk);
        voxelizerPool.submit(() -> processChunk(level, chunk));
    }

    private void processChunk(ServerLevel level, LevelChunk chunk) {
        try {
            LodSection lod0 = ChunkVoxelizer.voxelize(chunk);
            String dimKey = dimensionKey(level);
            SqliteSectionStorage storage = storageManager.get(dimKey);

            // Only store + broadcast if data actually changed
            if (!storage.isCurrent(lod0.key, lod0.hash)) {
                storage.store(lod0);
                broadcastSectionUpdate(level, lod0);
                generateHigherLods(level, storage, lod0);
            }
        } catch (Exception e) {
            LOGGER.error("[Voxy] Failed to voxelize chunk {}", chunk.getPos(), e);
        }
    }

    /**
     * Generates LOD levels 1 through {@code maxLodLevel} by mipping upward from the
     * newly updated LOD-0 section.  Only stores and broadcasts levels that actually change.
     */
    private void generateHigherLods(ServerLevel level, SqliteSectionStorage storage, LodSection lod0) {
        int maxLod = VoxyConfig.INSTANCE.serverMaxLodLevel.get();
        String dimKey = dimensionKey(level);
        LodSection current = lod0;

        for (int lodLevel = 1; lodLevel <= maxLod; lodLevel++) {
            int childLod = lodLevel - 1;
            int parentX  = SectionKey.sectionX(current.key) >> 1;
            int parentZ  = SectionKey.sectionZ(current.key) >> 1;

            // Load the other 3 siblings needed for mipping
            int baseX = parentX * 2;
            int baseZ = parentZ * 2;
            LodSection[] children = new LodSection[4];
            boolean allPresent = true;

            for (int dz = 0; dz <= 1; dz++) {
                for (int dx = 0; dx <= 1; dx++) {
                    long childKey = SectionKey.encode(childLod, baseX + dx, baseZ + dz);
                    var opt = storage.load(childKey);
                    if (opt.isEmpty()) { allPresent = false; break; }
                    children[dx + dz * 2] = opt.get();
                }
                if (!allPresent) break;
            }

            if (!allPresent) break; // can't mip yet

            LodSection mipped = LodMipper.mip(children);
            if (!storage.isCurrent(mipped.key, mipped.hash)) {
                storage.store(mipped);
                broadcastSectionUpdate(level, mipped);
            }
            current = mipped;
        }
    }

    // -------------------------------------------------------------------------
    // Manifest / transfer
    // -------------------------------------------------------------------------

    /** Builds and returns the current manifest for the given dimension. */
    public WorldManifest buildManifest(ResourceLocation dimension) {
        return storageManager.get(dimension.toString()).buildManifest();
    }

    /**
     * Sends all requested sections to the specified player.
     * Called from {@link ServerNetworkHandler} when the client requests sections.
     */
    public void sendSectionsToPlayer(ServerPlayer player, ResourceLocation dimension,
                                     java.util.List<Long> requestedKeys) {
        String dimKey = dimension.toString();
        SqliteSectionStorage storage = storageManager.get(dimKey);
        int maxQueue = VoxyConfig.INSTANCE.serverMaxTransferQueuePerClient.get();
        int sent = 0;

        for (Long key : requestedKeys) {
            if (sent >= maxQueue) {
                LOGGER.warn("[Voxy] Transfer queue limit reached for player {}", player.getName().getString());
                break;
            }
            storage.load(key).ifPresent(section -> {
                LodSectionDataPayload pkt = new LodSectionDataPayload(
                        dimension, section.key, section.hash, section.toBytes());
                PacketDistributor.sendToPlayer(player, pkt);
            });
            sent++;
        }
    }

    // -------------------------------------------------------------------------
    // Broadcast helpers
    // -------------------------------------------------------------------------

    private void broadcastSectionUpdate(ServerLevel level, LodSection section) {
        ResourceLocation dim = level.dimension().location();
        LodSectionDataPayload pkt = new LodSectionDataPayload(
                dim, section.key, section.hash, section.toBytes());
        // Send to all players currently in this dimension
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, pkt);
        }
    }

    public void broadcastSectionRemove(ServerLevel level, long sectionKey) {
        ResourceLocation dim = level.dimension().location();
        SectionRemovePayload pkt = new SectionRemovePayload(dim, sectionKey);
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, pkt);
        }
    }

    /** Exposed for use by {@link ChunkEventHandler} on dimension unload. */
    public SectionStorageManager getStorageManager() {
        return storageManager;
    }

    // -------------------------------------------------------------------------

    private static String dimensionKey(ServerLevel level) {
        return level.dimension().location().toString();
    }

    @Override
    public void close() {
        voxelizerPool.shutdown();
        try {
            if (!voxelizerPool.awaitTermination(10, TimeUnit.SECONDS)) {
                voxelizerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        storageManager.close();
        LOGGER.info("[Voxy] Server LOD manager stopped");
    }
}
