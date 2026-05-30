package me.cortex.voxy.common.lod;

import me.cortex.voxy.VoxyConstants;

import java.util.Objects;

/**
 * Merges four fine-LOD sections into a single coarser-LOD section.
 *
 * <p>Children are arranged in a 2×2 grid in chunk-space:
 * <pre>
 *   [0] = (2x,   2z)   [1] = (2x+1, 2z)
 *   [2] = (2x,   2z+1) [3] = (2x+1, 2z+1)
 * </pre>
 * The output section covers the same world area at LOD {@code N+1}, position {@code (x, z)}.
 * Each output cell is chosen from the most-representative (highest / most opaque) child cell.
 */
public final class LodMipper {
    private LodMipper() {}

    private static final int S = VoxyConstants.SECTION_SIZE; // 16

    /**
     * Produces one LOD (N+1) section from four LOD N children.
     *
     * @param children array of exactly 4 sections in order [NW, NE, SW, SE] (see class javadoc)
     * @return merged section at LOD level {@code children[0].lodLevel + 1}
     */
    public static LodSection mip(LodSection[] children) {
        if (children.length != 4) throw new IllegalArgumentException("Expected exactly 4 children");
        for (LodSection c : children) Objects.requireNonNull(c, "child section must not be null");

        int childLod = SectionKey.lodLevel(children[0].key);
        // Base section X/Z of the parent (children[0] = 2x, 2z → parent = x, z)
        int cx0 = SectionKey.sectionX(children[0].key);
        int cz0 = SectionKey.sectionZ(children[0].key);
        int parentX = cx0 >> 1;
        int parentZ = cz0 >> 1;
        long parentKey = SectionKey.encode(childLod + 1, parentX, parentZ);

        int[] blockStates = new int[S * S];
        short[] heights   = new short[S * S];
        byte[] lightData  = new byte[S * S];
        int[] biomeIds    = new int[S * S];

        // Each parent cell (ox, oz) samples from a 2×2 block of child cells.
        // Children cover half-sections each, so:
        //   child 0 covers output x=[0..7], z=[0..7]
        //   child 1 covers output x=[8..15], z=[0..7]
        //   child 2 covers output x=[0..7], z=[8..15]
        //   child 3 covers output x=[8..15], z=[8..15]
        for (int oz = 0; oz < S; oz++) {
            for (int ox = 0; ox < S; ox++) {
                int childIdx = ((ox >= S / 2) ? 1 : 0) + ((oz >= S / 2) ? 2 : 0);
                int cx = (ox % (S / 2)) * 2;
                int cz = (oz % (S / 2)) * 2;

                // Sample the 2×2 child cells and pick the highest surface
                int best = pickBest(children[childIdx], cx, cz);
                int ci = best + cz / 2 * S / 2; // not used directly, use helper

                int outIdx = ox + oz * S;
                int srcIdx = pickBestIndex(children[childIdx], cx, cz);
                blockStates[outIdx] = children[childIdx].blockStates[srcIdx];
                heights[outIdx]     = children[childIdx].heights[srcIdx];
                lightData[outIdx]   = children[childIdx].lightData[srcIdx];
                biomeIds[outIdx]    = children[childIdx].biomeIds[srcIdx];
            }
        }

        return new LodSection(parentKey, blockStates, heights, lightData, biomeIds);
    }

    /**
     * Among the four cells at (cx, cz), (cx+1, cz), (cx, cz+1), (cx+1, cz+1)
     * returns the flat index of the one with the highest surface (most prominent).
     */
    private static int pickBestIndex(LodSection s, int cx, int cz) {
        int best = cx + cz * S;
        for (int dz = 0; dz <= 1; dz++) {
            for (int dx = 0; dx <= 1; dx++) {
                int idx = (cx + dx) + (cz + dz) * S;
                if (s.heights[idx] > s.heights[best]) best = idx;
            }
        }
        return best;
    }

    // suppress unused – kept for symmetry with pickBestIndex
    @SuppressWarnings("unused")
    private static int pickBest(LodSection s, int cx, int cz) {
        return pickBestIndex(s, cx, cz);
    }
}
