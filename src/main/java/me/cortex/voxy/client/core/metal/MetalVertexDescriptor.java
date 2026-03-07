package me.cortex.voxy.client.core.metal;

import me.cortex.voxy.client.core.gpu.IGpuVertexArray;
import me.cortex.voxy.common.util.TrackedObject;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11C.*;

/**
 * Metal implementation of IGpuVertexArray.
 *
 * Metal doesn't have VAOs. Instead, vertex layout is described by
 * MTLVertexDescriptor which is part of the render pipeline state.
 * This class captures the vertex layout configuration and the associated
 * buffer bindings, which are applied when creating render pipeline states
 * or encoding draw calls.
 */
public class MetalVertexDescriptor extends TrackedObject implements IGpuVertexArray {
    private final int id;
    private int stride;
    private int vertexBuffer;
    private int elementBuffer;
    private final List<AttributeDesc> attributes = new ArrayList<>();

    private static int NEXT_ID = 1;

    public MetalVertexDescriptor() {
        this.id = NEXT_ID++;
    }

    @Override
    public int id() {
        return this.id;
    }

    @Override
    public void bind() {
        // Metal doesn't have a global VAO bind. The vertex descriptor is applied
        // per-pipeline-state and buffers are set on the render command encoder.
        // This is a no-op; the MetalRenderBackend tracks the "current" descriptor.
    }

    @Override
    public IGpuVertexArray bindBuffer(int buffer) {
        this.vertexBuffer = buffer;
        return this;
    }

    @Override
    public IGpuVertexArray bindElementBuffer(int buffer) {
        this.elementBuffer = buffer;
        return this;
    }

    @Override
    public IGpuVertexArray setStride(int stride) {
        this.stride = stride;
        return this;
    }

    @Override
    public IGpuVertexArray setI(int index, int type, int count, int offset) {
        this.attributes.add(new AttributeDesc(index, type, count, offset, false, true));
        return this;
    }

    @Override
    public IGpuVertexArray setF(int index, int type, int count, int offset) {
        return this.setF(index, type, count, false, offset);
    }

    @Override
    public IGpuVertexArray setF(int index, int type, int count, boolean normalize, int offset) {
        this.attributes.add(new AttributeDesc(index, type, count, offset, normalize, false));
        return this;
    }

    @Override
    public void free() {
        super.free0();
        // No native resources to release
    }

    // ---- Accessors for pipeline state creation ----

    public int getStride() {
        return this.stride;
    }

    public int getVertexBuffer() {
        return this.vertexBuffer;
    }

    public int getElementBuffer() {
        return this.elementBuffer;
    }

    public List<AttributeDesc> getAttributes() {
        return this.attributes;
    }

    /**
     * Describes a single vertex attribute for Metal vertex descriptor creation.
     */
    public record AttributeDesc(int index, int glType, int count, int offset,
                                 boolean normalize, boolean integer) {

        /**
         * Converts this attribute to a Metal vertex format constant.
         * Maps GL types (GL_FLOAT, GL_UNSIGNED_INT, etc.) to MTLVertexFormat values.
         */
        public int toMetalVertexFormat() {
            if (this.integer) {
                return switch (this.glType) {
                    case GL_UNSIGNED_INT -> switch (this.count) {
                        case 1 -> 36;  // MTLVertexFormatUInt
                        case 2 -> 37;  // MTLVertexFormatUInt2
                        case 3 -> 38;  // MTLVertexFormatUInt3
                        case 4 -> 39;  // MTLVertexFormatUInt4
                        default -> throw new IllegalArgumentException("Unsupported count: " + count);
                    };
                    case GL_INT -> switch (this.count) {
                        case 1 -> 32;  // MTLVertexFormatInt
                        case 2 -> 33;  // MTLVertexFormatInt2
                        case 3 -> 34;  // MTLVertexFormatInt3
                        case 4 -> 35;  // MTLVertexFormatInt4
                        default -> throw new IllegalArgumentException("Unsupported count: " + count);
                    };
                    case GL_UNSIGNED_SHORT -> switch (this.count) {
                        case 1 -> 20;  // MTLVertexFormatUShort
                        case 2 -> 21;  // MTLVertexFormatUShort2
                        case 3 -> 22;  // MTLVertexFormatUShort3
                        case 4 -> 23;  // MTLVertexFormatUShort4
                        default -> throw new IllegalArgumentException("Unsupported count: " + count);
                    };
                    case GL_UNSIGNED_BYTE -> switch (this.count) {
                        case 1 -> 45;  // MTLVertexFormatUChar
                        case 2 -> 1;   // MTLVertexFormatUChar2
                        case 3 -> 2;   // MTLVertexFormatUChar3
                        case 4 -> 3;   // MTLVertexFormatUChar4
                        default -> throw new IllegalArgumentException("Unsupported count: " + count);
                    };
                    default -> throw new IllegalArgumentException(
                            "Unsupported GL type for integer attribute: 0x" + Integer.toHexString(glType));
                };
            } else {
                if (this.glType == GL_FLOAT) {
                    return switch (this.count) {
                        case 1 -> 28;  // MTLVertexFormatFloat
                        case 2 -> 29;  // MTLVertexFormatFloat2
                        case 3 -> 30;  // MTLVertexFormatFloat3
                        case 4 -> 31;  // MTLVertexFormatFloat4
                        default -> throw new IllegalArgumentException("Unsupported count: " + count);
                    };
                }
                // Normalized integer types
                if (this.normalize) {
                    return switch (this.glType) {
                        case GL_UNSIGNED_BYTE -> switch (this.count) {
                            case 2 -> 5;   // MTLVertexFormatUChar2Normalized
                            case 3 -> 6;   // MTLVertexFormatUChar3Normalized
                            case 4 -> 7;   // MTLVertexFormatUChar4Normalized
                            default -> throw new IllegalArgumentException("Unsupported count: " + count);
                        };
                        case GL_UNSIGNED_SHORT -> switch (this.count) {
                            case 2 -> 25;  // MTLVertexFormatUShort2Normalized
                            case 3 -> 26;  // MTLVertexFormatUShort3Normalized
                            case 4 -> 27;  // MTLVertexFormatUShort4Normalized
                            default -> throw new IllegalArgumentException("Unsupported count: " + count);
                        };
                        default -> throw new IllegalArgumentException(
                                "Unsupported GL type for normalized attribute: 0x" + Integer.toHexString(glType));
                    };
                }
                throw new IllegalArgumentException(
                        "Unsupported GL type for float attribute: 0x" + Integer.toHexString(glType));
            }
        }
    }
}
