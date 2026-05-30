package me.cortex.voxy.client;

import me.cortex.voxy.common.lod.LodSection;
import me.cortex.voxy.common.lod.WorldManifest;
import me.cortex.voxy.network.payload.RequestManifestPayload;
import me.cortex.voxy.network.payload.RequestSectionsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static net.neoforged.neoforge.network.PacketDistributor.sendToServer;

/**
 * Client-side LOD manager.
 *
 * <ul>
 *   <li>Requests the server manifest on world join.
 *   <li>Diffs the manifest against the local {@link ClientCacheManager}.
 *   <li>Dispatches chunk requests in batches to avoid overwhelming the server.
 *   <li>Exposes received/cached sections for the render layer.
 * </ul>
 *
 * One instance exists per server connection lifetime (created in {@link VoxyClient}).
 */
public final class ClientLodManager implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientLodManager.class);
    private static final int REQUEST_BATCH_SIZE = 256;

    private final ClientCacheManager cache;

    /** In-memory live section map for the current dimension: sectionKey → LodSection. */
    private final ConcurrentHashMap<Long, LodSection> liveSections = new ConcurrentHashMap<>();

    // Sync statistics
    private final AtomicInteger pendingRequests = new AtomicInteger(0);
    private final AtomicLong    receivedBytes   = new AtomicLong(0);

    public ClientLodManager(String serverAddress) {
        this.cache = new ClientCacheManager(serverAddress);
    }

    // -------------------------------------------------------------------------
    // World lifecycle
    // -------------------------------------------------------------------------

    /**
     * Called when the client joins (or switches to) a dimension.
     * Warms the local index and requests the server manifest.
     */
    public void onDimensionJoin(ResourceLocation dimension) {
        LOGGER.info("[Voxy] Joined dimension '{}' — warming cache index", dimension);
        cache.warmIndex(dimension);
        sendToServer(new RequestManifestPayload(dimension));
    }

    // -------------------------------------------------------------------------
    // Packet handling (called from ClientNetworkHandler)
    // -------------------------------------------------------------------------

    /** Called when the server sends the world manifest. */
    public void onManifestReceived(ResourceLocation dimension, WorldManifest serverManifest) {
        Map<Long, Long> localIndex = cache.getLocalHashIndex(dimension);
        List<Long> needed = WorldManifest.diff(serverManifest, localIndex);

        if (needed.isEmpty()) {
            LOGGER.info("[Voxy] Cache is fully up to date for '{}'", dimension);
            return;
        }

        LOGGER.info("[Voxy] Requesting {} section(s) from server for '{}'", needed.size(), dimension);
        pendingRequests.addAndGet(needed.size());

        // Send in batches so we don't create one huge packet
        for (int i = 0; i < needed.size(); i += REQUEST_BATCH_SIZE) {
            List<Long> batch = needed.subList(i, Math.min(i + REQUEST_BATCH_SIZE, needed.size()));
            sendToServer(new RequestSectionsPayload(dimension, batch));
        }
    }

    /** Called when the server delivers section data. */
    public void onSectionDataReceived(ResourceLocation dimension, long sectionKey, long hash, byte[] data) {
        LodSection section = LodSection.fromBytes(sectionKey, data);
        cache.store(dimension, section);
        liveSections.put(sectionKey, section);
        receivedBytes.addAndGet(data.length);
        pendingRequests.decrementAndGet();
    }

    /** Called when the server removes a section. */
    public void onSectionRemoved(ResourceLocation dimension, long sectionKey) {
        cache.remove(dimension, sectionKey);
        liveSections.remove(sectionKey);
    }

    // -------------------------------------------------------------------------
    // Render access
    // -------------------------------------------------------------------------

    /** Returns the live section map (read only from the render thread). */
    public ConcurrentHashMap<Long, LodSection> getLiveSections() {
        return liveSections;
    }

    // -------------------------------------------------------------------------
    // Statistics
    // -------------------------------------------------------------------------

    public int getPendingRequests() { return pendingRequests.get(); }
    public long getReceivedBytes()  { return receivedBytes.get(); }

    // -------------------------------------------------------------------------

    @Override
    public void close() {
        liveSections.clear();
        cache.close();
    }

    // -------------------------------------------------------------------------

    /** Resolves the server address string used as the cache key. */
    public static String resolveServerAddress() {
        ServerData serverData = Minecraft.getInstance().getCurrentServer();
        if (serverData != null) return serverData.ip;
        return "singleplayer";
    }
}
