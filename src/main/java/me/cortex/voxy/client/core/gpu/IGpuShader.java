package me.cortex.voxy.client.core.gpu;

/**
 * Abstraction over a GPU shader program.
 */
public interface IGpuShader extends IGpuResource {
    int id();
    void bind();
    IGpuShader name(String name);
}
