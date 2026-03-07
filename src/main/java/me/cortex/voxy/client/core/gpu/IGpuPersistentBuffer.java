package me.cortex.voxy.client.core.gpu;

/**
 * Abstraction over a persistently-mapped GPU buffer for streaming data.
 */
public interface IGpuPersistentBuffer extends IGpuResource {
    int id();
    long size();
    long addr();
    IGpuPersistentBuffer name(String name);
}
