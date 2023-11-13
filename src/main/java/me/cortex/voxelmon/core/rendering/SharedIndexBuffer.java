package me.cortex.voxelmon.core.rendering;

import me.cortex.voxelmon.core.rendering.util.BufferArena;
import me.cortex.voxelmon.core.util.IndexUtil;

public class SharedIndexBuffer {
    public static final SharedIndexBuffer INSTANCE = new SharedIndexBuffer();

    private int commonIndexBufferOffset = -1;
    private int commonIndexQuadCount;

    private final BufferArena indexBuffer;

    public SharedIndexBuffer() {
        this.indexBuffer = new BufferArena((1L << 16)*(2*6), 2 * 6);
        this.getSharedIndexBuffer(16380);
    }

    public int getSharedIndexBuffer(int newQuadCount) {
        if (this.commonIndexBufferOffset == -1 || this.commonIndexQuadCount < newQuadCount) {
            if (this.commonIndexBufferOffset != -1) {
                this.indexBuffer.free(this.commonIndexBufferOffset);
            }
            var buffer = IndexUtil.generateQuadIndices(newQuadCount);
            long offset = this.indexBuffer.upload(buffer);
            if (offset >= 1L<<31) {
                throw new IllegalStateException();
            }
            this.commonIndexBufferOffset = (int) offset;
            buffer.free();
            this.commonIndexQuadCount = newQuadCount;
        }
        return this.commonIndexBufferOffset * 6;
    }

    public int id() {
        return this.indexBuffer.id();
    }
}
