package me.cortex.voxy.client.core.metal;

import me.cortex.voxy.client.core.gpu.IGpuBuffer;
import me.cortex.voxy.client.core.gpu.IGpuPipeline;
import me.cortex.voxy.client.core.gpu.IGpuTexture;
import me.cortex.voxy.client.core.gpu.RenderEncoder;

/**
 * Metal-side {@link RenderEncoder}. Wraps a single MTLRenderCommandEncoder
 * for the duration of a render pass; close() ends encoding and releases.
 *
 * Operations are delegated through the JNI surface in {@link MetalNative};
 * this class only translates the backend-agnostic primitive constants and
 * downcasts {@link IGpuPipeline} to its Metal-specific implementation.
 */
public final class MetalRenderEncoder implements RenderEncoder {

    private long encoderHandle;
    /** Index buffer remembered between bindIndexBuffer() and drawIndexed(). */
    private long boundIndexBuffer;
    private long boundIndexBufferOffset;
    private int boundIndexType = MetalNative.MTLIndexTypeUInt32;

    MetalRenderEncoder(long encoderHandle) {
        this.encoderHandle = encoderHandle;
    }

    @Override
    public void setPipeline(IGpuPipeline pipeline) {
        if (!(pipeline instanceof MetalGraphicsPipeline mp)) {
            throw new IllegalArgumentException(
                    "MetalRenderEncoder.setPipeline expected MetalGraphicsPipeline, got "
                            + (pipeline == null ? "null" : pipeline.getClass().getName()));
        }
        MetalNative.mtlRenderEncoderSetRenderPipelineState(this.encoderHandle, mp.pipelineStateHandle());
    }

    @Override
    public void setBuffer(int binding, IGpuBuffer buffer, long offset) {
        long handle = bufferHandle(buffer);
        // Voxy GLSL exposes the same binding to vertex + fragment, so bind both.
        // Cost is one redundant native call when only one stage reads the buffer;
        // on Metal that's cheap and the pattern matches Voxy's semantics today.
        MetalNative.mtlRenderEncoderSetVertexBuffer(this.encoderHandle, handle, offset, binding);
        MetalNative.mtlRenderEncoderSetFragmentBuffer(this.encoderHandle, handle, offset, binding);
    }

    @Override
    public void setTexture(int binding, IGpuTexture texture) {
        long handle = texture == null ? 0 : MetalHandleMap.getHandle(texture.id());
        MetalNative.mtlRenderEncoderSetVertexTexture(this.encoderHandle, handle, binding);
        MetalNative.mtlRenderEncoderSetFragmentTexture(this.encoderHandle, handle, binding);
    }

    @Override
    public void bindVertexBuffer(int slot, IGpuBuffer buffer, long offset) {
        long handle = bufferHandle(buffer);
        MetalNative.mtlRenderEncoderSetVertexBuffer(this.encoderHandle, handle, offset, slot);
    }

    @Override
    public void bindIndexBuffer(IGpuBuffer buffer, int indexType, long offset) {
        this.boundIndexBuffer = bufferHandle(buffer);
        this.boundIndexBufferOffset = offset;
        this.boundIndexType = switch (indexType) {
            case INDEX_TYPE_UINT16 -> MetalNative.MTLIndexTypeUInt16;
            case INDEX_TYPE_UINT32 -> MetalNative.MTLIndexTypeUInt32;
            default -> throw new IllegalArgumentException("Unsupported index type: " + indexType);
        };
    }

    @Override
    public void setViewport(float x, float y, float width, float height,
                             float minDepth, float maxDepth) {
        MetalNative.mtlRenderEncoderSetViewport(this.encoderHandle,
                x, y, width, height, minDepth, maxDepth);
    }

    @Override
    public void setScissor(int x, int y, int width, int height) {
        MetalNative.mtlRenderEncoderSetScissorRect(this.encoderHandle, x, y, width, height);
    }

    @Override
    public void draw(int primitiveType, int firstVertex, int vertexCount,
                     int instanceCount, int baseInstance) {
        int metalPrimitive = mapPrimitiveType(primitiveType);
        MetalNative.mtlRenderEncoderDrawPrimitives(this.encoderHandle,
                metalPrimitive, firstVertex, vertexCount, instanceCount, baseInstance);
    }

    @Override
    public void drawIndexed(int primitiveType, int indexCount, int instanceCount,
                             int firstIndex, int vertexOffset, int firstInstance) {
        if (this.boundIndexBuffer == 0) {
            throw new IllegalStateException("drawIndexed() before bindIndexBuffer()");
        }
        int metalPrimitive = mapPrimitiveType(primitiveType);
        // firstIndex is fed into the index buffer offset because Metal's
        // drawIndexedPrimitives doesn't take a firstIndex argument; it's
        // baked into indexBufferOffset. indexType drives the multiplier.
        long indexBytes = this.boundIndexType == MetalNative.MTLIndexTypeUInt16 ? 2L : 4L;
        long offset = this.boundIndexBufferOffset + (long) firstIndex * indexBytes;
        MetalNative.mtlRenderEncoderDrawIndexedPrimitives(this.encoderHandle,
                metalPrimitive, indexCount, this.boundIndexType,
                this.boundIndexBuffer, offset,
                instanceCount, vertexOffset, firstInstance);
    }

    @Override
    public void drawIndirect(int primitiveType, IGpuBuffer buffer, long offset,
                              int drawCount, int stride) {
        long indirectBuf = bufferHandle(buffer);
        if (indirectBuf == 0) throw new IllegalArgumentException("drawIndirect: indirect buffer is null");
        int metalPrimitive = mapPrimitiveType(primitiveType);
        // Metal lacks native multi-draw-indirect; loop on the host. Each iteration
        // dispatches one indirect draw at offset + i*stride. For drawCount=1 this
        // is a single call; for larger counts we accept the per-call overhead
        // until M11+ wires up an MTLIndirectCommandBuffer cache.
        for (int i = 0; i < drawCount; i++) {
            MetalNative.mtlRenderEncoderDrawPrimitivesIndirect(this.encoderHandle,
                    metalPrimitive, indirectBuf, offset + (long) i * stride);
        }
    }

    @Override
    public void drawIndexedIndirect(int primitiveType, IGpuBuffer buffer, long offset,
                                     int drawCount, int stride) {
        if (this.boundIndexBuffer == 0) {
            throw new IllegalStateException("drawIndexedIndirect() before bindIndexBuffer()");
        }
        long indirectBuf = bufferHandle(buffer);
        if (indirectBuf == 0) throw new IllegalArgumentException("drawIndexedIndirect: indirect buffer is null");
        int metalPrimitive = mapPrimitiveType(primitiveType);
        for (int i = 0; i < drawCount; i++) {
            MetalNative.mtlRenderEncoderDrawIndexedPrimitivesIndirect(this.encoderHandle,
                    metalPrimitive, this.boundIndexType,
                    this.boundIndexBuffer, this.boundIndexBufferOffset,
                    indirectBuf, offset + (long) i * stride);
        }
    }

    @Override
    public void close() {
        if (this.encoderHandle == 0) return;
        MetalNative.mtlEncoderEndEncoding(this.encoderHandle);
        MetalNative.mtlRelease(this.encoderHandle);
        this.encoderHandle = 0;
    }

    private static long bufferHandle(IGpuBuffer buffer) {
        if (buffer == null) return 0;
        if (!(buffer instanceof MetalBuffer mb)) {
            throw new IllegalArgumentException(
                    "MetalRenderEncoder expected MetalBuffer, got " + buffer.getClass().getName());
        }
        return mb.handle();
    }

    private static int mapPrimitiveType(int abstractType) {
        return switch (abstractType) {
            case PRIMITIVE_TRIANGLES -> MetalNative.MTLPrimitiveTypeTriangle;
            case PRIMITIVE_TRIANGLE_STRIP -> MetalNative.MTLPrimitiveTypeTriangleStrip;
            case PRIMITIVE_LINES -> MetalNative.MTLPrimitiveTypeLine;
            case PRIMITIVE_POINTS -> MetalNative.MTLPrimitiveTypePoint;
            default -> throw new IllegalArgumentException("Unsupported primitive type: " + abstractType);
        };
    }
}
