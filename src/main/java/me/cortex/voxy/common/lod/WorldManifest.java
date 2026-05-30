package me.cortex.voxy.common.lod;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * An immutable snapshot of all LOD sections known for one world dimension.
 *
 * <p>Maps {@link SectionKey}-encoded longs to the 64-bit content hash of each section.
 * Clients use this to determine which sections are missing or out of date in their local cache.
 */
public final class WorldManifest {

    private final Map<Long, Long> sectionHashes; // sectionKey → dataHash

    public WorldManifest(Map<Long, Long> sectionHashes) {
        this.sectionHashes = Collections.unmodifiableMap(new HashMap<>(sectionHashes));
    }

    /** Returns the hash for the given section key, or {@code -1} if not present. */
    public long hashFor(long sectionKey) {
        Long h = sectionHashes.get(sectionKey);
        return h != null ? h : -1L;
    }

    public boolean contains(long sectionKey) {
        return sectionHashes.containsKey(sectionKey);
    }

    /** All section keys tracked by this manifest. */
    public Set<Long> keys() {
        return sectionHashes.keySet();
    }

    public Map<Long, Long> asMap() {
        return sectionHashes;
    }

    public int size() {
        return sectionHashes.size();
    }

    /**
     * Computes the set of section keys the client needs to request from the server.
     *
     * @param clientCache local manifest (section key → cached hash), may be empty
     * @return keys present in {@code serverManifest} but absent or stale in {@code clientCache}
     */
    public static java.util.List<Long> diff(WorldManifest serverManifest, Map<Long, Long> clientCache) {
        java.util.List<Long> needed = new java.util.ArrayList<>();
        for (Map.Entry<Long, Long> entry : serverManifest.asMap().entrySet()) {
            Long cachedHash = clientCache.get(entry.getKey());
            if (cachedHash == null || !cachedHash.equals(entry.getValue())) {
                needed.add(entry.getKey());
            }
        }
        return needed;
    }
}
