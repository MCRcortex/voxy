package me.cortex.voxy.common.lod;

import java.util.zip.CRC32C;

/**
 * Computes a 64-bit hash for {@link LodSection} data using CRC32C.
 * The hash is combined from block states, heights, biomes, and light data
 * so any change in any field produces a different hash value.
 */
public final class LodSectionHash {
    private LodSectionHash() {}

    public static long compute(int[] blockStates, short[] heights, byte[] lightData, int[] biomeIds) {
        CRC32C crc = new CRC32C();
        crc.update(toBytes(blockStates), 0, blockStates.length * 4);
        crc.update(toBytes(heights), 0, heights.length * 2);
        crc.update(lightData, 0, lightData.length);
        crc.update(toBytes(biomeIds), 0, biomeIds.length * 4);
        long lo = crc.getValue();

        // Second pass over biomes + blockStates reversed for better avalanche
        crc.reset();
        crc.update(toBytes(biomeIds), 0, biomeIds.length * 4);
        crc.update(toBytes(blockStates), 0, blockStates.length * 4);
        long hi = crc.getValue();

        return (hi << 32) | (lo & 0xFFFFFFFFL);
    }

    // --- helpers ---

    private static byte[] toBytes(int[] arr) {
        byte[] out = new byte[arr.length * 4];
        for (int i = 0; i < arr.length; i++) {
            int v = arr[i];
            out[i * 4    ] = (byte) (v >>> 24);
            out[i * 4 + 1] = (byte) (v >>> 16);
            out[i * 4 + 2] = (byte) (v >>>  8);
            out[i * 4 + 3] = (byte)  v;
        }
        return out;
    }

    private static byte[] toBytes(short[] arr) {
        byte[] out = new byte[arr.length * 2];
        for (int i = 0; i < arr.length; i++) {
            short v = arr[i];
            out[i * 2    ] = (byte) (v >>> 8);
            out[i * 2 + 1] = (byte)  v;
        }
        return out;
    }
}
