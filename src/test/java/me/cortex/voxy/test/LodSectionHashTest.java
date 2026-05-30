package me.cortex.voxy.test;

import me.cortex.voxy.common.lod.LodSectionHash;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LodSectionHashTest {

    private static final int CELLS = 16 * 16;

    @Test
    void sameDataProducesSameHash() {
        int[]   bs = new int[CELLS];
        short[] ht = new short[CELLS];
        byte[]  lt = new byte[CELLS];
        int[]   bi = new int[CELLS];

        long h1 = LodSectionHash.compute(bs, ht, lt, bi);
        long h2 = LodSectionHash.compute(bs, ht, lt, bi);
        assertEquals(h1, h2, "identical data must produce the same hash");
    }

    @Test
    void differentBlockStateProducesDifferentHash() {
        int[]   bs1 = new int[CELLS];
        int[]   bs2 = new int[CELLS];
        bs2[0] = 42; // change one block
        short[] ht = new short[CELLS];
        byte[]  lt = new byte[CELLS];
        int[]   bi = new int[CELLS];

        long h1 = LodSectionHash.compute(bs1, ht, lt, bi);
        long h2 = LodSectionHash.compute(bs2, ht, lt, bi);
        assertNotEquals(h1, h2, "different block states must produce different hashes");
    }

    @Test
    void differentHeightProducesDifferentHash() {
        int[]   bs = new int[CELLS];
        short[] ht1 = new short[CELLS];
        short[] ht2 = new short[CELLS];
        ht2[5] = 64;
        byte[]  lt = new byte[CELLS];
        int[]   bi = new int[CELLS];

        assertNotEquals(
                LodSectionHash.compute(bs, ht1, lt, bi),
                LodSectionHash.compute(bs, ht2, lt, bi),
                "different heights must produce different hashes");
    }

    @Test
    void differentBiomeProducesDifferentHash() {
        int[]   bs = new int[CELLS];
        short[] ht = new short[CELLS];
        byte[]  lt = new byte[CELLS];
        int[]   bi1 = new int[CELLS];
        int[]   bi2 = new int[CELLS];
        bi2[100] = 7;

        assertNotEquals(
                LodSectionHash.compute(bs, ht, lt, bi1),
                LodSectionHash.compute(bs, ht, lt, bi2),
                "different biomes must produce different hashes");
    }
}
