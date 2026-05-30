package me.cortex.voxy.test;

import me.cortex.voxy.common.lod.LodMipper;
import me.cortex.voxy.common.lod.LodSection;
import me.cortex.voxy.common.lod.SectionKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LodMipperTest {

    private static LodSection makeSection(int lod, int x, int z, int blockFill, short heightFill) {
        int cells = 16 * 16;
        int[]   bs = new int[cells];
        short[] ht = new short[cells];
        byte[]  lt = new byte[cells];
        int[]   bi = new int[cells];
        for (int i = 0; i < cells; i++) {
            bs[i] = blockFill;
            ht[i] = heightFill;
        }
        return new LodSection(SectionKey.encode(lod, x, z), bs, ht, lt, bi);
    }

    @Test
    void mipProducesCorrectLodLevel() {
        LodSection[] children = {
            makeSection(0, 0, 0, 1, (short) 64),
            makeSection(0, 1, 0, 2, (short) 64),
            makeSection(0, 0, 1, 3, (short) 64),
            makeSection(0, 1, 1, 4, (short) 64),
        };
        LodSection parent = LodMipper.mip(children);
        assertEquals(1, SectionKey.lodLevel(parent.key), "Parent LOD level must be 1");
    }

    @Test
    void mipProducesCorrectParentCoordinates() {
        LodSection[] children = {
            makeSection(0, 4, 6, 1, (short) 10),
            makeSection(0, 5, 6, 1, (short) 10),
            makeSection(0, 4, 7, 1, (short) 10),
            makeSection(0, 5, 7, 1, (short) 10),
        };
        LodSection parent = LodMipper.mip(children);
        assertEquals(2, SectionKey.sectionX(parent.key));
        assertEquals(3, SectionKey.sectionZ(parent.key));
    }

    @Test
    void mipPicksHighestSurface() {
        short LOW  = 10;
        short HIGH = 200;
        // Child 0 has low height everywhere, child 1 has high height at one cell
        LodSection[] children = {
            makeSection(0, 0, 0, 1, LOW),
            makeSection(0, 1, 0, 2, HIGH),
            makeSection(0, 0, 1, 3, LOW),
            makeSection(0, 1, 1, 4, LOW),
        };
        LodSection parent = LodMipper.mip(children);

        // Cells in the right-half of the parent (ox >= 8) come from child 1 or 3
        // At minimum one output cell should have height == HIGH
        boolean foundHigh = false;
        for (short h : parent.heights) {
            if (h == HIGH) { foundHigh = true; break; }
        }
        assertTrue(foundHigh, "Mipped parent should contain the high-surface cell");
    }

    @Test
    void mipRequiresExactlyFourChildren() {
        assertThrows(IllegalArgumentException.class, () -> LodMipper.mip(new LodSection[3]));
        assertThrows(IllegalArgumentException.class, () -> LodMipper.mip(new LodSection[5]));
    }

    @Test
    void mipRejectsNullChild() {
        LodSection[] children = {
            makeSection(0, 0, 0, 1, (short) 0),
            null,
            makeSection(0, 0, 1, 3, (short) 0),
            makeSection(0, 1, 1, 4, (short) 0),
        };
        assertThrows(NullPointerException.class, () -> LodMipper.mip(children));
    }
}
