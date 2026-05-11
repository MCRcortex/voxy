package me.cortex.voxy.client.core.metal;

import me.cortex.voxy.client.core.gpu.IGpuPipeline;

/**
 * Metal implementation of {@link IGpuPipeline}: holds a MTLRenderPipelineState
 * plus the libraries and functions it depends on so they stay alive for the
 * pipeline's lifetime.
 *
 * Closing this object releases all four handles in reverse-creation order
 * (state → fragment fn → vertex fn → library). The libraries are owned 1:1
 * with the pipeline today; M11+ will introduce a library cache when shader
 * permutation pressure justifies it.
 */
public final class MetalGraphicsPipeline implements IGpuPipeline {

    private long pipelineState;
    private long vertexLibrary;
    private long fragmentLibrary;
    private long vertexFunction;
    private long fragmentFunction;
    /** Depth-stencil state object — 0 if no depth testing/writing requested. */
    private long depthStencilState;
    /** Encoder-time raster state pulled from PipelineState; applied each setPipeline. */
    final int cullMode;
    final int winding;
    final int fillMode;

    MetalGraphicsPipeline(long pipelineState, long vertexLibrary, long fragmentLibrary,
                          long vertexFunction, long fragmentFunction,
                          long depthStencilState, int cullMode, int winding, int fillMode) {
        this.pipelineState = pipelineState;
        this.vertexLibrary = vertexLibrary;
        this.fragmentLibrary = fragmentLibrary;
        this.vertexFunction = vertexFunction;
        this.fragmentFunction = fragmentFunction;
        this.depthStencilState = depthStencilState;
        this.cullMode = cullMode;
        this.winding = winding;
        this.fillMode = fillMode;
    }

    public long pipelineStateHandle() {
        return this.pipelineState;
    }

    long depthStencilStateHandle() {
        return this.depthStencilState;
    }

    @Override
    public void close() {
        if (this.pipelineState != 0) {
            MetalNative.mtlRelease(this.pipelineState);
            this.pipelineState = 0;
        }
        if (this.depthStencilState != 0) {
            MetalNative.mtlRelease(this.depthStencilState);
            this.depthStencilState = 0;
        }
        if (this.fragmentFunction != 0) {
            MetalNative.mtlRelease(this.fragmentFunction);
            this.fragmentFunction = 0;
        }
        if (this.vertexFunction != 0) {
            MetalNative.mtlRelease(this.vertexFunction);
            this.vertexFunction = 0;
        }
        if (this.fragmentLibrary != 0) {
            MetalNative.mtlRelease(this.fragmentLibrary);
            this.fragmentLibrary = 0;
        }
        if (this.vertexLibrary != 0) {
            MetalNative.mtlRelease(this.vertexLibrary);
            this.vertexLibrary = 0;
        }
    }
}
