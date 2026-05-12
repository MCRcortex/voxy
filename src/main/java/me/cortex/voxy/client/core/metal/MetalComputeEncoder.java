package me.cortex.voxy.client.core.metal;

import me.cortex.voxy.client.core.gpu.ComputeEncoder;
import me.cortex.voxy.client.core.gpu.IGpuBuffer;
import me.cortex.voxy.client.core.gpu.IGpuPipeline;
import me.cortex.voxy.client.core.gpu.IGpuSampler;
import me.cortex.voxy.client.core.gpu.IGpuTexture;

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
    public void setBuffer(int binding, me.cortex.voxy.client.core.gpu.IGpuPersistentBuffer buffer, long offset, long size) {
        if (!(buffer instanceof MetalPersistentBuffer mpb)) {
            throw new IllegalArgumentException(
                    "MetalComputeEncoder.setBuffer(IGpuPersistentBuffer) expected MetalPersistentBuffer, got "
                            + (buffer == null ? "null" : buffer.getClass().getName()));
        }
        // Metal's setBuffer:offset:atIndex: doesn't carry a "size" — the
        // shader's binding type determines the read range. `size` is GL's
        // glBindBufferRange parameter; we accept it here for cross-backend
        // signature parity but only the offset is meaningful on Metal.
        MetalNative.mtlComputeEncoderSetBuffer(this.encoderHandle, mpb.getHandle(), offset, binding);
    }

    @Override
    public void setTexture(int binding, IGpuTexture texture) {
        long handle = texture == null ? 0 : MetalHandleMap.getHandle(texture.id());
        MetalNative.mtlComputeEncoderSetTexture(this.encoderHandle, handle, binding);
    }

    @Override
    public void setStorageImage(int binding, IGpuTexture texture, int level) {
        // Metal's setTexture:atIndex: handles sampled and storage uniformly;
        // both shader-side `texture2d<...>` and `texture2d<..., access::write>`
        // pull from the same MTLTexture slot. Per-mip views require a new
        // `newTextureViewWithPixelFormat:textureType:levels:slices:` JNI; for
        // now mip 0 is the only supported level. (HiZBuffer migration in M9
        // adds the per-mip JNI as a separate change.)
        if (level != 0) {
            throw new UnsupportedOperationException(
                    "MetalComputeEncoder.setStorageImage: level != 0 needs the per-mip "
                            + "texture-view JNI from M9 Phase 2; got level=" + level);
        }
        long handle = texture == null ? 0 : MetalHandleMap.getHandle(texture.id());
        MetalNative.mtlComputeEncoderSetTexture(this.encoderHandle, handle, binding);
    }

    @Override
    public void setSampler(int binding, IGpuSampler sampler) {
        long handle = 0;
        if (sampler != null) {
            if (!(sampler instanceof MetalSampler ms)) {
                throw new IllegalArgumentException("MetalComputeEncoder.setSampler expected MetalSampler, got "
                        + sampler.getClass().getName());
            }
            handle = ms.handle();
        }
        MetalNative.mtlComputeEncoderSetSamplerState(this.encoderHandle, handle, binding);
    }

    @Override
    public void setBytes(int binding, long dataAddr, int dataSize) {
        MetalNative.mtlComputeEncoderSetBytes(this.encoderHandle, dataAddr, dataSize, binding);
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
    public void dispatchIndirect(IGpuBuffer buffer, long offset) {
        if (this.boundPipeline == null) {
            throw new IllegalStateException("dispatchIndirect() before setPipeline()");
        }
        if (!(buffer instanceof MetalBuffer mb)) {
            throw new IllegalArgumentException(
                    "MetalComputeEncoder.dispatchIndirect expected MetalBuffer, got "
                            + (buffer == null ? "null" : buffer.getClass().getName()));
        }
        MetalNative.mtlComputeEncoderDispatchThreadgroupsIndirect(this.encoderHandle,
                mb.handle(), offset,
                this.boundPipeline.localSizeX,
                this.boundPipeline.localSizeY,
                this.boundPipeline.localSizeZ);
    }

    @Override
    public void barrier(int srcStages, int dstStages) {
        // Map our cross-backend stage flags onto Metal's MTLBarrierScope. The
        // src/dst distinction collapses: Metal's scope describes which resource
        // categories need synchronization, regardless of direction. We OR the
        // resource categories implied by either stage.
        int merged = srcStages | dstStages;
        int scope = 0;
        if ((merged & (BARRIER_SHADER | BARRIER_INDIRECT)) != 0) {
            scope |= MetalNative.MTLBarrierScopeBuffers;
            // Storage-image bindings count as textures; safest to flush both.
            scope |= MetalNative.MTLBarrierScopeTextures;
        }
        if ((merged & BARRIER_TRANSFER) != 0) {
            // Blit/transfer happens outside the compute encoder; dropping the
            // intra-encoder barrier is safe — hazard tracking handles it.
        }
        if (scope != 0) {
            MetalNative.mtlComputeEncoderMemoryBarrier(this.encoderHandle, scope);
        }
    }

    // Re-export so the constants are visible without a static import on the caller side.
    private static final int BARRIER_SHADER = ComputeEncoder.BARRIER_SHADER;
    private static final int BARRIER_INDIRECT = ComputeEncoder.BARRIER_INDIRECT;
    private static final int BARRIER_TRANSFER = ComputeEncoder.BARRIER_TRANSFER;

    @Override
    public void close() {
        if (this.encoderHandle == 0) return;
        MetalNative.mtlEncoderEndEncoding(this.encoderHandle);
        MetalNative.mtlRelease(this.encoderHandle);
        this.encoderHandle = 0;
    }
}
