package me.cortex.voxy.client.core.util;

import me.cortex.voxy.common.util.MemoryBuffer;
import org.lwjgl.system.MemoryUtil;

public class IndexUtil {
    public static MemoryBuffer generateQuadIndicesByte(int quadCount) {
        if ((quadCount*4) >= 1<<8) {
            throw new IllegalArgumentException("Quad count to large");
        }
        MemoryBuffer buffer = new MemoryBuffer(quadCount * 6L);
        long ptr = buffer.address;
        for(int i = 0; i < quadCount*4; i += 4) {
            MemoryUtil.memPutByte(ptr + (0), (byte) i);
            MemoryUtil.memPutByte(ptr + (1), (byte) (i + 1));
            MemoryUtil.memPutByte(ptr + (2), (byte) (i + 2));
            MemoryUtil.memPutByte(ptr + (3), (byte) (i + 1));
            MemoryUtil.memPutByte(ptr + (4), (byte) (i + 3));
            MemoryUtil.memPutByte(ptr + (5), (byte) (i + 2));

            ptr += 6;
        }

        return buffer;
    }
    public static MemoryBuffer generateQuadIndicesShort(int quadCount) {
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

    public static MemoryBuffer generateQuadIndicesInt(int quadCount) {
        MemoryBuffer buffer = new MemoryBuffer(quadCount * 6L * 2);
        long ptr = buffer.address;
        for(int i = 0; i < quadCount*4; i += 4) {
            MemoryUtil.memPutInt(ptr + (0*4), i);
            MemoryUtil.memPutInt(ptr + (1*4), (i + 1));
            MemoryUtil.memPutInt(ptr + (2*4), (i + 2));
            MemoryUtil.memPutInt(ptr + (3*4), (i + 1));
            MemoryUtil.memPutInt(ptr + (4*4), (i + 3));
            MemoryUtil.memPutInt(ptr + (5*4), (i + 2));
            ptr += 6 * 4;
        }
        return buffer;
    }
}
