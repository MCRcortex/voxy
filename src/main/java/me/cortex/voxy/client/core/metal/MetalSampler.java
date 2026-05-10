package me.cortex.voxy.client.core.metal;

import me.cortex.voxy.client.core.gpu.IGpuSampler;

/**
 * Metal implementation of {@link IGpuSampler} — wraps an MTLSamplerState.
 * close() releases the underlying handle; the sampler state object is
 * shared across draws/dispatches and is cheap to keep around.
 */
public final class MetalSampler implements IGpuSampler {

    private long handle;

    MetalSampler(long handle) {
        this.handle = handle;
    }

    long handle() {
        return this.handle;
    }

    @Override
    public void close() {
        if (this.handle != 0) {
            MetalNative.mtlRelease(this.handle);
            this.handle = 0;
        }
    }
}
