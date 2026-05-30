package me.cortex.voxy.test;

import me.cortex.voxy.common.lod.SectionKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SectionKeyTest {

    @Test
    void encodeDecodeRoundTrip() {
        check(0, 0, 0);
        check(0, 100, -50);
        check(4, -134217728, 134217727); // extreme edges of 28-bit signed range
        check(7, 1, 1);
    }

    @Test
    void lodLevelExtracted() {
        for (int lod = 0; lod <= 7; lod++) {
            long key = SectionKey.encode(lod, 0, 0);
            assertEquals(lod, SectionKey.lodLevel(key));
        }
    }

    @Test
    void negativeCoordinates() {
        long key = SectionKey.encode(0, -1, -1);
        assertEquals(-1, SectionKey.sectionX(key));
        assertEquals(-1, SectionKey.sectionZ(key));
    }

    @Test
    void differentPositionsProduceDifferentKeys() {
        assertNotEquals(
                SectionKey.encode(0, 1, 0),
                SectionKey.encode(0, 0, 1));
        assertNotEquals(
                SectionKey.encode(0, 1, 1),
                SectionKey.encode(1, 1, 1));
    }

    @Test
    void invalidLodLevelThrows() {
        assertThrows(IllegalArgumentException.class, () -> SectionKey.encode(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> SectionKey.encode(256, 0, 0));
    }

    private static void check(int lod, int x, int z) {
        long key = SectionKey.encode(lod, x, z);
        assertEquals(lod, SectionKey.lodLevel(key), "lod mismatch for " + lod + "," + x + "," + z);
        assertEquals(x,   SectionKey.sectionX(key), "x mismatch for " + lod + "," + x + "," + z);
        assertEquals(z,   SectionKey.sectionZ(key), "z mismatch for " + lod + "," + x + "," + z);
    }
}
