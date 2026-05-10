package me.cortex.voxy.client.core.metal;

import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_RGBA8;
import static org.lwjgl.opengl.GL14C.GL_DEPTH_COMPONENT24;
import static org.lwjgl.opengl.GL14C.GL_DEPTH_COMPONENT32;
import static org.lwjgl.opengl.GL30C.*;

/**
 * Utility for converting between OpenGL format constants and Metal pixel formats.
 *
 * This is needed because the abstraction layer interfaces use GL constants for
 * format specification (since GL is the primary backend), and the Metal backend
 * needs to translate these to MTLPixelFormat values.
 */
public final class MetalFormatUtil {

    private MetalFormatUtil() {}

    // Metal Pixel Format constants (MTLPixelFormat enum values)
    public static final int MTLPixelFormatRGBA8Unorm     = 70;
    public static final int MTLPixelFormatRGBA8Uint      = 73;
    public static final int MTLPixelFormatR32Uint        = 53;
    public static final int MTLPixelFormatR32Float       = 55;
    public static final int MTLPixelFormatR8Uint         = 13;
    public static final int MTLPixelFormatDepth32Float   = 252;
    public static final int MTLPixelFormatDepth24Unorm_Stencil8 = 255; // macOS only
    public static final int MTLPixelFormatDepth32Float_Stencil8 = 260;

    /**
     * Converts an OpenGL internal format to a Metal pixel format.
     * @param glFormat OpenGL internal format (e.g. GL_RGBA8, GL_R32UI)
     * @return MTLPixelFormat value
     */
    public static int glFormatToMetal(int glFormat) {
        return switch (glFormat) {
            case GL_RGBA8 -> MTLPixelFormatRGBA8Unorm;
            case GL_R32UI -> MTLPixelFormatR32Uint;
            case GL_R32F -> MTLPixelFormatR32Float;
            case GL_R8UI -> MTLPixelFormatR8Uint;
            case GL_DEPTH_COMPONENT32F -> MTLPixelFormatDepth32Float;
            // Apple Silicon GPUs DO NOT support Depth24Unorm_Stencil8 (that
            // format is macOS-Intel only). Use the 32-bit float depth formats
            // instead — fully lossless re-encoding for the values Voxy stores.
            case GL_DEPTH_COMPONENT24 -> MTLPixelFormatDepth32Float;
            case GL_DEPTH_COMPONENT32 -> MTLPixelFormatDepth32Float;
            case GL_DEPTH24_STENCIL8 -> MTLPixelFormatDepth32Float_Stencil8;
            default -> throw new IllegalArgumentException(
                    "Unsupported GL format for Metal conversion: 0x" + Integer.toHexString(glFormat));
        };
    }

    /**
     * Returns the bytes per pixel for a given GL internal format.
     */
    public static long bytesPerPixel(int glFormat) {
        return switch (glFormat) {
            case GL_R8UI -> 1;
            case GL_RGBA8, GL_R32UI, GL_R32F,
                 GL_DEPTH_COMPONENT24, GL_DEPTH24_STENCIL8,
                 GL_DEPTH_COMPONENT32, GL_DEPTH_COMPONENT32F -> 4;
            default -> throw new IllegalArgumentException(
                    "Unknown bytes-per-pixel for format: 0x" + Integer.toHexString(glFormat));
        };
    }

    /**
     * Converts an OpenGL texture type to a Metal texture type.
     */
    public static int glTextureTypeToMetal(int glTextureType) {
        return switch (glTextureType) {
            case GL_TEXTURE_2D -> MetalNative.MTLTextureType2D;
            default -> throw new IllegalArgumentException(
                    "Unsupported GL texture type for Metal: 0x" + Integer.toHexString(glTextureType));
        };
    }

    /**
     * Converts an OpenGL framebuffer attachment constant to a Metal attachment index.
     * GL_COLOR_ATTACHMENT0..n maps to Metal color attachment 0..n.
     * GL_DEPTH_ATTACHMENT and GL_DEPTH_STENCIL_ATTACHMENT are special.
     */
    public static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    public static final int GL_DEPTH_ATTACHMENT = 0x8D00;
    public static final int GL_STENCIL_ATTACHMENT = 0x8D20;
    public static final int GL_DEPTH_STENCIL_ATTACHMENT = 0x821A;

    public static boolean isColorAttachment(int glAttachment) {
        return glAttachment >= GL_COLOR_ATTACHMENT0 && glAttachment < GL_COLOR_ATTACHMENT0 + 16;
    }

    public static int colorAttachmentIndex(int glAttachment) {
        return glAttachment - GL_COLOR_ATTACHMENT0;
    }

    public static boolean isDepthAttachment(int glAttachment) {
        return glAttachment == GL_DEPTH_ATTACHMENT || glAttachment == GL_DEPTH_STENCIL_ATTACHMENT;
    }

    public static boolean isStencilAttachment(int glAttachment) {
        return glAttachment == GL_STENCIL_ATTACHMENT || glAttachment == GL_DEPTH_STENCIL_ATTACHMENT;
    }
}
