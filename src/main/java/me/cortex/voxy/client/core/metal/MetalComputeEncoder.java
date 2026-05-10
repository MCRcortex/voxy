package me.cortex.voxy.client.core.metal;

import me.cortex.voxy.client.core.gpu.ComputeEncoder;
import me.cortex.voxy.client.core.gpu.IGpuBuffer;
import me.cortex.voxy.client.core.gpu.IGpuPipeline;

/**
 * Metal-side {@link ComputeEncoder}: wraps a single MTLComputeCommandEncoder
 * for the duration of a compute pass. Tracks the bound pipeline so
 * {@link #dispatch} can supply Metal's required {@code threadsPerThreadgroup}
 * argument from the shader's declared local size.
 */
public final class MetalComputeEncoder implements ComputeEncoder {

    private long encoderHandle;
    private MetalComputePipeline boundPipeline;

    MetalComputeEncoder(long encoderHandle) {
        this.encoderHandle = encoderHandle;
    }

    @Override
    public void setPipeline(IGpuPipeline pipeline) {
        if (!(pipeline instanceof MetalComputePipeline mp)) {
            throw new IllegalArgumentException(
                    "MetalComputeEncoder.setPipeline expected MetalComputePipeline, got "
                            + (pipeline == null ? "null" : pipeline.getClass().getName()));
        }
        MetalNative.mtlComputeEncoderSetComputePipelineState(this.encoderHandle, mp.pipelineStateHandle());
        this.boundPipeline = mp;
    }

    @Override
    public void setBuffer(int binding, IGpuBuffer buffer, long offset) {
        if (!(buffer instanceof MetalBuffer mb)) {
            throw new IllegalArgumentException(
                    "MetalComputeEncoder.setBuffer expected MetalBuffer, got "
                            + (buffer == null ? "null" : buffer.getClass().getName()));
        }
        MetalNative.mtlComputeEncoderSetBuffer(this.encoderHandle, mb.handle(), offset, binding);
    }

    @Override
    public void dispatch(int groupCountX, int groupCountY, int groupCountZ) {
        if (this.boundPipeline == null) {
            throw new IllegalStateException("dispatch() before setPipeline() — Metal needs the local size from the bound pipeline");
        }
        MetalNative.mtlComputeEncoderDispatchThreadgroups(this.encoderHandle,
                groupCountX, groupCountY, groupCountZ,
                this.boundPipeline.localSizeX,
                this.boundPipeline.localSizeY,
                this.boundPipeline.localSizeZ);
    }

    @Override
    public void close() {
        if (this.encoderHandle == 0) return;
        MetalNative.mtlEncoderEndEncoding(this.encoderHandle);
        MetalNative.mtlRelease(this.encoderHandle);
        this.encoderHandle = 0;
    }
}
