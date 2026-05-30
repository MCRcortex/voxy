package me.cortex.voxy.common.lod;

/**
 * Encodes/decodes a section position as a single {@code long}.
 *
 * <pre>
 * Bit layout (64 bits):
 *   63-56  lod level  (8 bits,  0-255)
 *   55-28  section X  (28 bits, signed via bias)
 *   27- 0  section Z  (28 bits, signed via bias)
 * </pre>
 *
 * Section coordinates at LOD N map to the chunk-space grid divided by 2^N.
 * E.g. LOD 0 section (3, 7) covers chunk (3, 7).
 *      LOD 1 section (1, 3) covers chunks (2, 6), (3, 6), (2, 7), (3, 7).
 */
public final class SectionKey {
    private SectionKey() {}

    private static final int Z_BITS = 28;
    private static final long Z_MASK = (1L << Z_BITS) - 1L;
    private static final long XZ_BIAS = 1L << (Z_BITS - 1); // 2^27

    public static long encode(int lodLevel, int sectionX, int sectionZ) {
        if (lodLevel < 0 || lodLevel > 255) throw new IllegalArgumentException("lodLevel out of range: " + lodLevel);
        long lx = (sectionX + XZ_BIAS) & Z_MASK;
        long lz = (sectionZ + XZ_BIAS) & Z_MASK;
        return ((long) lodLevel << 56) | (lx << Z_BITS) | lz;
    }

    public static int lodLevel(long key) {
        return (int) (key >>> 56);
    }

    public static int sectionX(long key) {
        return (int) ((key >> Z_BITS) & Z_MASK) - (int) XZ_BIAS;
    }

    public static int sectionZ(long key) {
        return (int) (key & Z_MASK) - (int) XZ_BIAS;
    }

    @Override
    public String toString() {
        throw new UnsupportedOperationException("Use SectionKey.toString(long)");
    }

    public static String toString(long key) {
        return String.format("SectionKey[lod=%d, x=%d, z=%d]", lodLevel(key), sectionX(key), sectionZ(key));
    }
}
