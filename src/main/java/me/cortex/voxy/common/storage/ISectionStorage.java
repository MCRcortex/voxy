package me.cortex.voxy.common.storage;

import me.cortex.voxy.common.lod.LodSection;
import me.cortex.voxy.common.lod.WorldManifest;

import java.io.Closeable;
import java.util.Optional;

/**
 * Persistence interface for LOD sections in a single world dimension.
 *
 * <p>Implementations are expected to be thread-safe.
 */
public interface ISectionStorage extends Closeable {

    /**
     * Stores (or replaces) a section.
     * The section's {@link LodSection#hash} is saved alongside the data.
     */
    void store(LodSection section);

    /**
     * Loads the section for the given key, or empty if not present.
     */
    Optional<LodSection> load(long sectionKey);

    /**
     * Returns {@code true} if a section with this key and hash already exists
     * (i.e. is up to date and does not need to be re-stored).
     */
    boolean isCurrent(long sectionKey, long hash);

    /**
     * Removes the section for the given key.  No-op if it does not exist.
     */
    void remove(long sectionKey);

    /**
     * Returns a manifest of all stored section keys mapped to their hashes.
     * Used to generate the delta sent to newly connected clients.
     */
    WorldManifest buildManifest();

    /**
     * Flushes any pending writes to disk.
     */
    void flush();
}
