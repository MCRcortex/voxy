package me.cortex.voxy.client.core.metal;

import me.cortex.voxy.client.core.gpu.IGpuPipeline;
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
    public void draw(int primitiveType, int firstVertex, int vertexCount,
                     int instanceCount, int baseInstance) {
        int metalPrimitive = mapPrimitiveType(primitiveType);
        MetalNative.mtlRenderEncoderDrawPrimitives(this.encoderHandle,
                metalPrimitive, firstVertex, vertexCount, instanceCount, baseInstance);
    }

    @Override
    public void close() {
        if (this.encoderHandle == 0) return;
        MetalNative.mtlEncoderEndEncoding(this.encoderHandle);
        MetalNative.mtlRelease(this.encoderHandle);
        this.encoderHandle = 0;
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
