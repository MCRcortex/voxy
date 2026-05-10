package me.cortex.voxy.client.core.gpu;

/**
 * Backend-agnostic sampler state object.
 *
 * Wraps an MTLSamplerState (Metal), VkSampler (Vulkan), or a GL sampler
 * object. Samplers are cheap, immutable, and shared across draws — caller
 * keeps the handle alive for as long as it's referenced and calls
 * {@link #close} when finished.
 */
public interface IGpuSampler extends AutoCloseable {
    @Override
    void close();
}
