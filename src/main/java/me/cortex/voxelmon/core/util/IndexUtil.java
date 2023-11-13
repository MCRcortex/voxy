package me.cortex.voxelmon.core.util;

import org.lwjgl.system.MemoryUtil;

public class IndexUtil {
    public static MemoryBuffer generateQuadIndices(int quadCount) {
        if ((quadCount*4) >= 1<<16) {
            throw new IllegalArgumentException("Quad count to large");
        }
        MemoryBuffer buffer = new MemoryBuffer(quadCount * 6L * 2);
        long ptr = buffer.address;
        for(int i = 0; i < quadCount*4; i += 4) {
            MemoryUtil.memPutShort(ptr + (0*2), (short) i);
            MemoryUtil.memPutShort(ptr + (1*2), (short) (i + 1));
            MemoryUtil.memPutShort(ptr + (2*2), (short) (i + 2));
            MemoryUtil.memPutShort(ptr + (3*2), (short) (i + 1));
            MemoryUtil.memPutShort(ptr + (4*2), (short) (i + 3));
            MemoryUtil.memPutShort(ptr + (5*2), (short) (i + 2));

            ptr += 6 * 2;
        }

        return buffer;
    }
}
