package me.cortex.voxy.client.core.gl;

import me.cortex.voxy.client.core.gpu.VertexLayout.VertexFormat;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL30C;

/**
 * Maps cross-backend {@link VertexFormat} enums onto the GL
 * {@code (type, count, normalized, isInteger)} tuple used by
 * {@code glVertexAttribFormat} / {@code glVertexAttribIFormat}.
 *
 * Kept separate from {@link GlRenderBackend} so the table is testable in
 * isolation and easy to extend as M9 migration uncovers more formats.
 */
final class GlVertexFormatMap {

    private GlVertexFormatMap() {
    }

    /** GL primitive type (GL_FLOAT, GL_INT, GL_UNSIGNED_BYTE, ...). */
    static int glType(VertexFormat f) {
        return switch (f) {
            case FLOAT, FLOAT2, FLOAT3, FLOAT4 -> GL11C.GL_FLOAT;
            case INT, INT2, INT3, INT4 -> GL11C.GL_INT;
            case UINT, UINT2, UINT3, UINT4 -> GL11C.GL_UNSIGNED_INT;
            case UBYTE4_NORM -> GL11C.GL_UNSIGNED_BYTE;
            case BYTE4_NORM -> GL11C.GL_BYTE;
            case USHORT2, USHORT4, USHORT2_NORM, USHORT4_NORM -> GL11C.GL_UNSIGNED_SHORT;
            case HALF2, HALF4 -> GL30C.GL_HALF_FLOAT;
        };
    }

    /** Component count (1..4). */
    static int components(VertexFormat f) {
        return switch (f) {
            case FLOAT, INT, UINT -> 1;
            case FLOAT2, INT2, UINT2, USHORT2, USHORT2_NORM, HALF2 -> 2;
            case FLOAT3, INT3, UINT3 -> 3;
            case FLOAT4, INT4, UINT4, UBYTE4_NORM, BYTE4_NORM, USHORT4, USHORT4_NORM, HALF4 -> 4;
        };
    }

    /** Whether the GL driver should normalize integer values to [-1,1]/[0,1]. */
    static boolean normalized(VertexFormat f) {
        return switch (f) {
            case UBYTE4_NORM, BYTE4_NORM, USHORT2_NORM, USHORT4_NORM -> true;
            default -> false;
        };
    }

    /**
     * Whether the attribute is an integer type that should bind through
     * {@code glVertexAttribIFormat} instead of {@code glVertexAttribFormat}.
     * Normalized integer formats stay as float attributes (per GL spec).
     */
    static boolean integerAttribute(VertexFormat f) {
        return switch (f) {
            case INT, INT2, INT3, INT4,
                 UINT, UINT2, UINT3, UINT4,
                 USHORT2, USHORT4 -> true;
            default -> false;
        };
    }
}
