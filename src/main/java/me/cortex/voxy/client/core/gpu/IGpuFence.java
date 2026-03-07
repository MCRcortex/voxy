package me.cortex.voxy.client.core.gpu;

/**
 * Abstraction over a GPU synchronization fence.
 */
public interface IGpuFence extends IGpuResource {
    boolean signaled();
}
