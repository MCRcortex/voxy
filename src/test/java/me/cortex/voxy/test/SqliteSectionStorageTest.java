package me.cortex.voxy.test;

import me.cortex.voxy.common.lod.LodSection;
import me.cortex.voxy.common.lod.SectionKey;
import me.cortex.voxy.common.lod.WorldManifest;
import me.cortex.voxy.common.storage.SqliteSectionStorage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SqliteSectionStorageTest {

    @TempDir
    static Path tempDir;

    static SqliteSectionStorage storage;

    static final long KEY_A = SectionKey.encode(0, 3, 7);
    static final long KEY_B = SectionKey.encode(0, 3, 8);

    @BeforeAll
    static void openStorage() throws SQLException {
        storage = new SqliteSectionStorage(tempDir.resolve("test_sections.db"));
    }

    @AfterAll
    static void closeStorage() throws Exception {
        if (storage != null) storage.close();
    }

    private static LodSection makeSection(long key, int blockFill) {
        int cells = 16 * 16;
        int[]   bs = new int[cells];
        short[] ht = new short[cells];
        byte[]  lt = new byte[cells];
        int[]   bi = new int[cells];
        for (int i = 0; i < cells; i++) bs[i] = blockFill;
        return new LodSection(key, bs, ht, lt, bi);
    }

    @Test @Order(1)
    void storeAndLoad() {
        LodSection s = makeSection(KEY_A, 1);
        storage.store(s);

        Optional<LodSection> loaded = storage.load(KEY_A);
        assertTrue(loaded.isPresent(), "Section should be present after store");
        assertEquals(KEY_A, loaded.get().key);
        assertEquals(s.hash, loaded.get().hash);
    }

    @Test @Order(2)
    void isCurrentReturnsTrueForMatchingHash() {
        LodSection s = makeSection(KEY_A, 1);
        assertTrue(storage.isCurrent(KEY_A, s.hash));
    }

    @Test @Order(3)
    void isCurrentReturnsFalseForWrongHash() {
        assertFalse(storage.isCurrent(KEY_A, 0xDEADBEEFL));
    }

    @Test @Order(4)
    void updateOverwritesSection() {
        LodSection updated = makeSection(KEY_A, 99);
        storage.store(updated);

        Optional<LodSection> loaded = storage.load(KEY_A);
        assertTrue(loaded.isPresent());
        assertEquals(updated.hash, loaded.get().hash);
    }

    @Test @Order(5)
    void removeDeletesSection() {
        storage.store(makeSection(KEY_B, 5));
        storage.remove(KEY_B);
        assertTrue(storage.load(KEY_B).isEmpty(), "Section should be absent after remove");
    }

    @Test @Order(6)
    void buildManifestContainsAllStoredKeys() {
        WorldManifest manifest = storage.buildManifest();
        assertTrue(manifest.contains(KEY_A), "Manifest must include KEY_A");
        assertFalse(manifest.contains(KEY_B), "Manifest must not include removed KEY_B");
    }

    @Test @Order(7)
    void roundTripPreservesData() {
        int cells = 16 * 16;
        int[]   bs = new int[cells];
        short[] ht = new short[cells];
        byte[]  lt = new byte[cells];
        int[]   bi = new int[cells];

        for (int i = 0; i < cells; i++) {
            bs[i] = i * 3;
            ht[i] = (short) (i - cells / 2);
            lt[i] = (byte) (i & 0xFF);
            bi[i] = i % 50;
        }

        long key = SectionKey.encode(0, -1, -1);
        LodSection original = new LodSection(key, bs, ht, lt, bi);
        storage.store(original);

        LodSection loaded = storage.load(key).orElseThrow();
        assertArrayEquals(original.blockStates, loaded.blockStates);
        assertArrayEquals(original.heights,     loaded.heights);
        assertArrayEquals(original.lightData,   loaded.lightData);
        assertArrayEquals(original.biomeIds,    loaded.biomeIds);
    }
}
