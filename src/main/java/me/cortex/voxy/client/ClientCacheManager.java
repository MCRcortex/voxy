package me.cortex.voxy.client;

import me.cortex.voxy.common.lod.LodSection;
import me.cortex.voxy.common.lod.WorldManifest;
import me.cortex.voxy.common.storage.SectionStorageManager;
import me.cortex.voxy.common.storage.SqliteSectionStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages the client-side disk cache for received LOD sections.
 *
 * <p>Cache layout on disk:
 * <pre>
 *   {minecraft_dir}/voxy-cache/{server_hash}/{dimension}/sections.db
 * </pre>
 *
 * <p>The server hash is derived from the server address so different servers get separate caches.
 */
public final class ClientCacheManager implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientCacheManager.class);

    private final SectionStorageManager storageManager;
    private final ReentrantReadWriteLock memoryLock = new ReentrantReadWriteLock();

    /** In-memory hash index for fast manifest diff without hitting SQLite. */
    private final Map<ResourceLocation, Map<Long, Long>> memoryIndex = new HashMap<>();

    public ClientCacheManager(String serverIdentifier) {
        Path cacheRoot = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("voxy-cache")
                .resolve(sanitize(serverIdentifier));
        this.storageManager = new SectionStorageManager(cacheRoot);
        LOGGER.info("[Voxy] Client cache initialised at {}", cacheRoot);
    }

    // -------------------------------------------------------------------------
    // Cache read / write
    // -------------------------------------------------------------------------

    /**
     * Stores a received section in the local cache and updates the in-memory index.
     */
    public void store(ResourceLocation dimension, LodSection section) {
        SqliteSectionStorage storage = storageManager.get(dimension.toString());
        storage.store(section);

        memoryLock.writeLock().lock();
        try {
            memoryIndex.computeIfAbsent(dimension, k -> new HashMap<>())
                    .put(section.key, section.hash);
        } finally {
            memoryLock.writeLock().unlock();
        }
    }

    /**
     * Removes a section from cache (e.g. server indicates it was deleted).
     */
    public void remove(ResourceLocation dimension, long sectionKey) {
        storageManager.get(dimension.toString()).remove(sectionKey);

        memoryLock.writeLock().lock();
        try {
            Map<Long, Long> idx = memoryIndex.get(dimension);
            if (idx != null) idx.remove(sectionKey);
        } finally {
            memoryLock.writeLock().unlock();
        }
    }

    /**
     * Loads a section from the local cache, or empty if not cached.
     */
    public Optional<LodSection> load(ResourceLocation dimension, long sectionKey) {
        return storageManager.get(dimension.toString()).load(sectionKey);
    }

    /**
     * Returns the local hash index for the given dimension (a snapshot copy).
     * Used to compute the manifest diff against the server's manifest.
     */
    public Map<Long, Long> getLocalHashIndex(ResourceLocation dimension) {
        memoryLock.readLock().lock();
        try {
            Map<Long, Long> idx = memoryIndex.get(dimension);
            return idx != null ? new HashMap<>(idx) : new HashMap<>();
        } finally {
            memoryLock.readLock().unlock();
        }
    }

    /**
     * Warm-loads the in-memory hash index from SQLite for a given dimension.
     * Should be called on world join before sending {@link RequestManifestPayload}.
     */
    public void warmIndex(ResourceLocation dimension) {
        SqliteSectionStorage storage = storageManager.get(dimension.toString());
        WorldManifest manifest = storage.buildManifest();

        memoryLock.writeLock().lock();
        try {
            memoryIndex.put(dimension, new HashMap<>(manifest.asMap()));
        } finally {
            memoryLock.writeLock().unlock();
        }
        LOGGER.info("[Voxy] Warmed index for {} ({} cached sections)", dimension, manifest.size());
    }

    // -------------------------------------------------------------------------

    @Override
    public void close() {
        storageManager.close();
    }

    private static String sanitize(String serverAddress) {
        // Use a short hex hash to avoid file-system path length issues
        return String.format("%08x", serverAddress.hashCode());
    }
}
