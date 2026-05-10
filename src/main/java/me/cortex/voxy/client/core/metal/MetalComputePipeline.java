package me.cortex.voxy.client.core.metal;

import me.cortex.voxy.client.core.gpu.IGpuPipeline;

/**
 * Metal implementation of {@link IGpuPipeline} for compute shaders. Holds
 * a MTLComputePipelineState, the MTLLibrary and MTLFunction it depends on,
 * plus the local thread-group size declared in the shader (so the encoder
 * can pass {@code threadsPerThreadgroup} when dispatching).
 *
 * close() releases all native handles in reverse-creation order.
 */
public final class MetalComputePipeline implements IGpuPipeline {

    private long pipelineState;
    private long library;
    private long function;
    final int localSizeX;
    final int localSizeY;
    final int localSizeZ;

    MetalComputePipeline(long pipelineState, long library, long function,
                         int localSizeX, int localSizeY, int localSizeZ) {
        this.pipelineState = pipelineState;
        this.library = library;
        this.function = function;
        this.localSizeX = localSizeX;
        this.localSizeY = localSizeY;
        this.localSizeZ = localSizeZ;
    }

    long pipelineStateHandle() {
        return this.pipelineState;
    }

    @Override
    public void close() {
        if (this.pipelineState != 0) {
            MetalNative.mtlRelease(this.pipelineState);
            this.pipelineState = 0;
        }
        if (this.function != 0) {
            MetalNative.mtlRelease(this.function);
            this.function = 0;
        }
        if (this.library != 0) {
            MetalNative.mtlRelease(this.library);
            this.library = 0;
        }
    }
}
