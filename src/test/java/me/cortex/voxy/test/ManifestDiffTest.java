package me.cortex.voxy.test;

import me.cortex.voxy.common.lod.LodSection;
import me.cortex.voxy.common.lod.SectionKey;
import me.cortex.voxy.common.lod.WorldManifest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ManifestDiffTest {

    private static LodSection makeSection(int lod, int x, int z, int blockFill) {
        int cells = 16 * 16;
        int[]   bs = new int[cells];
        short[] ht = new short[cells];
        byte[]  lt = new byte[cells];
        int[]   bi = new int[cells];
        for (int i = 0; i < cells; i++) bs[i] = blockFill;
        return new LodSection(SectionKey.encode(lod, x, z), bs, ht, lt, bi);
    }

    @Test
    void emptyClientCacheRequestsAll() {
        Map<Long, Long> serverMap = new HashMap<>();
        LodSection s1 = makeSection(0, 0, 0, 1);
        LodSection s2 = makeSection(0, 1, 0, 2);
        serverMap.put(s1.key, s1.hash);
        serverMap.put(s2.key, s2.hash);

        WorldManifest server = new WorldManifest(serverMap);
        List<Long> needed = WorldManifest.diff(server, Map.of());

        assertEquals(2, needed.size(), "Should need both sections");
        assertTrue(needed.contains(s1.key));
        assertTrue(needed.contains(s2.key));
    }

    @Test
    void upToDateCacheRequestsNothing() {
        LodSection s = makeSection(0, 5, 5, 7);
        WorldManifest server = new WorldManifest(Map.of(s.key, s.hash));
        List<Long> needed = WorldManifest.diff(server, Map.of(s.key, s.hash));
        assertTrue(needed.isEmpty(), "Up-to-date section must not be requested");
    }

    @Test
    void staleCacheRequestsChangedSection() {
        LodSection sOld = makeSection(0, 2, 2, 10);
        LodSection sNew = makeSection(0, 2, 2, 99); // same position, different data

        WorldManifest server = new WorldManifest(Map.of(sNew.key, sNew.hash));
        List<Long> needed = WorldManifest.diff(server, Map.of(sOld.key, sOld.hash));

        assertEquals(1, needed.size());
        assertEquals(sNew.key, needed.get(0));
    }

    @Test
    void serverManifestSizeReflectsStoredSections() {
        Map<Long, Long> map = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            LodSection s = makeSection(0, i, 0, i);
            map.put(s.key, s.hash);
        }
        WorldManifest manifest = new WorldManifest(map);
        assertEquals(100, manifest.size());
    }
}
