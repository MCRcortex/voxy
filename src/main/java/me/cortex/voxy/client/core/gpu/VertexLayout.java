package me.cortex.voxy.client.core.gpu;

import java.util.Arrays;

/**
 * Describes how vertex inputs are laid out in vertex buffer(s) for a graphics
 * pipeline. Used by {@link GraphicsPipelineDesc} when the vertex shader has
 * {@code in} attributes (instead of pulling vertex data from SSBOs via
 * {@code gl_VertexID}).
 *
 * Two-level structure mirrors Vulkan/Metal:
 *   - {@link VertexAttribute}: per-attribute (location, format, offset within
 *     a vertex, which buffer slot it comes from).
 *   - {@link VertexBufferBinding}: per-buffer-slot (stride, step rate).
 *
 * One {@link RenderEncoder#bindVertexBuffer bindVertexBuffer} call is needed
 * per declared {@link VertexBufferBinding} before drawing.
 */
public final class VertexLayout {

    public final VertexAttribute[] attributes;
    public final VertexBufferBinding[] buffers;

    public VertexLayout(VertexAttribute[] attributes, VertexBufferBinding[] buffers) {
        this.attributes = attributes;
        this.buffers = buffers;
    }

    /** Empty layout — used by shaders that read vertex data via gl_VertexIndex. */
    public static final VertexLayout EMPTY = new VertexLayout(new VertexAttribute[0], new VertexBufferBinding[0]);

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final java.util.ArrayList<VertexAttribute> attrs = new java.util.ArrayList<>(8);
        private final java.util.ArrayList<VertexBufferBinding> bufs = new java.util.ArrayList<>(2);

        public Builder buffer(int slot, int stride, StepRate stepRate) {
            this.bufs.add(new VertexBufferBinding(slot, stride, stepRate));
            return this;
        }

        public Builder attribute(int location, VertexFormat format, int offset, int bufferSlot) {
            this.attrs.add(new VertexAttribute(location, format, offset, bufferSlot));
            return this;
        }

        public VertexLayout build() {
            return new VertexLayout(this.attrs.toArray(new VertexAttribute[0]),
                                     this.bufs.toArray(new VertexBufferBinding[0]));
        }
    }

    /** Per-vertex vs per-instance buffer step. */
    public enum StepRate { PER_VERTEX, PER_INSTANCE }

    /**
     * One vertex attribute. {@code location} matches the {@code layout(location=N)}
     * decoration in the vertex shader; {@code bufferSlot} indexes into the
     * pipeline's {@link #buffers buffers} array (and is the slot used by
     * {@link RenderEncoder#bindVertexBuffer}).
     */
    public static final class VertexAttribute {
        public final int location;
        public final VertexFormat format;
        public final int offset;
        public final int bufferSlot;

        public VertexAttribute(int location, VertexFormat format, int offset, int bufferSlot) {
            this.location = location;
            this.format = format;
            this.offset = offset;
            this.bufferSlot = bufferSlot;
        }
    }

    /** One vertex buffer binding declaration. */
    public static final class VertexBufferBinding {
        public final int slot;
        public final int stride;
        public final StepRate stepRate;

        public VertexBufferBinding(int slot, int stride, StepRate stepRate) {
            this.slot = slot;
            this.stride = stride;
            this.stepRate = stepRate;
        }
    }

    /**
     * Cross-backend vertex format enum. Names follow {@code <type><count>[_norm]}
     * convention. Each backend maps these to its own constants — Metal
     * MTLVertexFormat, Vulkan VkFormat, OpenGL (type, count, normalized) tuple.
     *
     * The {@code metalValue} field carries the raw MTLVertexFormat enum value
     * so the Metal backend can pass it through JNI without a runtime switch.
     * Vulkan and GL translations happen in the respective backend wrappers.
     *
     * Initial set covers what Voxy's vertex shaders actually use today; more
     * formats can be added as M9 migration encounters them.
     */
    public enum VertexFormat {
        // Values match MTLVertexFormat from <Metal/MTLVertexDescriptor.h> exactly
        // — confirmed against the SDK header, NOT inferred from header order.
        FLOAT(28),
        FLOAT2(29),
        FLOAT3(30),
        FLOAT4(31),
        INT(32),
        INT2(33),
        INT3(34),
        INT4(35),
        UINT(36),
        UINT2(37),
        UINT3(38),
        UINT4(39),
        UBYTE4_NORM(9),       // MTLVertexFormatUChar4Normalized
        BYTE4_NORM(12),       // MTLVertexFormatChar4Normalized
        USHORT2(13),          // MTLVertexFormatUShort2
        USHORT4(15),          // MTLVertexFormatUShort4
        USHORT2_NORM(19),     // MTLVertexFormatUShort2Normalized
        USHORT4_NORM(21),     // MTLVertexFormatUShort4Normalized
        HALF2(25),
        HALF4(27);

        /** Raw MTLVertexFormat enum ordinal. */
        public final int metalValue;

        VertexFormat(int metalValue) {
            this.metalValue = metalValue;
        }
    }
}
