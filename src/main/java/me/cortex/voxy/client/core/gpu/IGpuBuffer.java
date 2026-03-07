package me.cortex.voxy.client.core.gpu;

/**
 * Abstraction over a GPU buffer (OpenGL buffer object, Metal buffer, etc.)
 * Implementations handle the underlying API-specific resource lifecycle.
 */
public interface IGpuBuffer extends IGpuResource {
    int id();
    long size();
    boolean isSparse();

    IGpuBuffer zero();
    IGpuBuffer zeroRange(long offset, long size);
    IGpuBuffer fill(int data);
    IGpuBuffer name(String name);
}
