package me.cortex.voxy.client.core.gpu;

/**
 * Base interface for all GPU resources that need lifecycle management.
 */
public interface IGpuResource {
    void free();
    void assertNotFreed();
    boolean isFreed();
}
