package me.cortex.voxy.client.core.gpu;

/**
 * Abstraction over a persistently-mapped GPU buffer for streaming data.
 */
public interface IGpuPersistentBuffer extends IGpuResource {
    int id();
    long size();
    long addr();
    IGpuPersistentBuffer name(String name);

    /**
     * Flushes a range of CPU-side writes so the GPU can observe them.
     * On OpenGL this maps to glFlushMappedBufferRange / glFlushMappedNamedBufferRange;
     * on Metal with shared storage (unified memory) this is a no-op because the
     * CPU and GPU share the same memory.
     */
    void flushRange(long offset, long length);
}
