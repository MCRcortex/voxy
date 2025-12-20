package me.cortex.voxy.client.core.gl;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.GL45C;

/**
 * Small compatibility layer to run on 4.3 without DSA by falling back to bind-to-edit calls.
 */
public final class GLCompat {
    private GLCompat() {}

    private static final boolean HAS_DSA = GL.getCapabilities().GL_ARB_direct_state_access || GL.getCapabilities().OpenGL45;

    public static int createTexture(int target) {
        if (HAS_DSA) {
            return GL45C.glCreateTextures(target);
        }
        return GL11C.glGenTextures();
    }

    public static void deleteTexture(int id) {
        GL11C.glDeleteTextures(id);
    }

    public static void textureStorage2D(int texture, int target, int levels, int format, int width, int height) {
        if (HAS_DSA) {
            GL45C.glTextureStorage2D(texture, levels, format, width, height);
        } else {
            int prev = GL11C.glGetInteger(bindingEnum(target));
            GL11C.glBindTexture(target, texture);
            GL42C.glTexStorage2D(target, levels, format, width, height);
            GL11C.glBindTexture(target, prev);
        }
    }

    public static void textureSubImage2D(int texture, int target, int level, int x, int y, int w, int h, int format, int type, long addr) {
        if (HAS_DSA) {
            GL45C.nglTextureSubImage2D(texture, level, x, y, w, h, format, type, addr);
        } else {
            int prev = GL11C.glGetInteger(bindingEnum(target));
            GL11C.glBindTexture(target, texture);
            GL11C.nglTexSubImage2D(target, level, x, y, w, h, format, type, addr);
            GL11C.glBindTexture(target, prev);
        }
    }

    public static void textureParameteri(int texture, int target, int pname, int param) {
        if (HAS_DSA) {
            GL45C.glTextureParameteri(texture, pname, param);
        } else {
            int prev = GL11C.glGetInteger(bindingEnum(target));
            GL11C.glBindTexture(target, texture);
            GL11C.glTexParameteri(target, pname, param);
            GL11C.glBindTexture(target, prev);
        }
    }

    public static void textureParameteri(int texture, int pname, int param) {
        textureParameteri(texture, GL11C.GL_TEXTURE_2D, pname, param);
    }

    public static void textureParameterf(int texture, int target, int pname, float param) {
        if (HAS_DSA) {
            GL45C.glTextureParameterf(texture, pname, param);
        } else {
            int prev = GL11C.glGetInteger(bindingEnum(target));
            GL11C.glBindTexture(target, texture);
            GL11C.glTexParameterf(target, pname, param);
            GL11C.glBindTexture(target, prev);
        }
    }

    public static void textureParameterf(int texture, int pname, float param) {
        textureParameterf(texture, GL11C.GL_TEXTURE_2D, pname, param);
    }

    public static void bindTextureUnit(int unit, int target, int texture) {
        if (HAS_DSA) {
            GL45C.glBindTextureUnit(unit, texture);
        } else {
            int prevActive = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
            int prev = GL11C.glGetInteger(bindingEnum(target));
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + unit);
            GL11C.glBindTexture(target, texture);
            GL13C.glActiveTexture(prevActive);
            GL11C.glBindTexture(target, prev);
        }
    }

    public static void bindTextureUnit(int unit, int texture) {
        bindTextureUnit(unit, GL11C.GL_TEXTURE_2D, texture);
    }

    public static int createFramebuffer() {
        if (HAS_DSA) {
            return GL45C.glCreateFramebuffers();
        }
        return GL30C.glGenFramebuffers();
    }

    public static void deleteFramebuffer(int id) {
        GL30C.glDeleteFramebuffers(id);
    }

    public static void framebufferTexture(int fbo, int attachment, int texture, int level, int target) {
        if (HAS_DSA) {
            GL45C.glNamedFramebufferTexture(fbo, attachment, texture, level);
        } else {
            int prev = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
            GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, attachment, target, texture, level);
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, prev);
        }
    }

    public static void framebufferRenderbuffer(int fbo, int attachment, int renderbuffer) {
        if (HAS_DSA) {
            GL45C.glNamedFramebufferRenderbuffer(fbo, attachment, GL30C.GL_RENDERBUFFER, renderbuffer);
        } else {
            int prev = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
            GL30C.glFramebufferRenderbuffer(GL30C.GL_FRAMEBUFFER, attachment, GL30C.GL_RENDERBUFFER, renderbuffer);
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, prev);
        }
    }

    public static void framebufferDrawBuffers(int fbo, int... buffers) {
        if (HAS_DSA) {
            GL45C.glNamedFramebufferDrawBuffers(fbo, buffers);
        } else {
            int prev = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
            GL20C.glDrawBuffers(buffers);
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, prev);
        }
    }

    public static int checkFramebufferStatus(int fbo) {
        if (HAS_DSA) {
            return GL45C.glCheckNamedFramebufferStatus(fbo, GL30C.GL_FRAMEBUFFER);
        }
        int prev = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
        int status = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, prev);
        return status;
    }

    public static void clearDepthFramebuffer(int fbo, float depth) {
        if (HAS_DSA) {
            org.lwjgl.opengl.ARBDirectStateAccess.nglClearNamedFramebufferfv(fbo, GL11C.GL_DEPTH, 0, org.lwjgl.system.MemoryStack.stackGet().nfloat(depth));
        } else {
            int prev = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
            GL30C.glClearBufferfv(GL11C.GL_DEPTH, 0, new float[]{depth});
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, prev);
        }
    }

    public static void clearDepthStencilFramebuffer(int fbo, float depth, int stencil) {
        if (HAS_DSA) {
            GL45C.glClearNamedFramebufferfi(fbo, GL30C.GL_DEPTH_STENCIL, 0, depth, stencil);
        } else {
            int prev = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
            GL30C.glClearBufferfi(GL30C.GL_DEPTH_STENCIL, 0, depth, stencil);
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, prev);
        }
    }

    public static void blitFramebuffer(int readFbo, int drawFbo, int srcX0, int srcY0, int srcX1, int srcY1,
                                       int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        if (HAS_DSA) {
            GL45C.glBlitNamedFramebuffer(readFbo, drawFbo, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
        } else {
            int prevRead = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
            int prevDraw = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, readFbo);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, drawFbo);
            GL30C.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, prevRead);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, prevDraw);
        }
    }

    public static int createRenderbuffer() {
        if (HAS_DSA) {
            return GL45C.glCreateRenderbuffers();
        }
        return GL30C.glGenRenderbuffers();
    }

    public static void renderbufferStorage(int renderbuffer, int format, int width, int height) {
        if (HAS_DSA) {
            GL45C.glNamedRenderbufferStorage(renderbuffer, format, width, height);
        } else {
            int prev = GL11C.glGetInteger(GL30C.GL_RENDERBUFFER_BINDING);
            GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, renderbuffer);
            GL30C.glRenderbufferStorage(GL30C.GL_RENDERBUFFER, format, width, height);
            GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, prev);
        }
    }

    private static int bindingEnum(int target) {
        return switch (target) {
            case GL11C.GL_TEXTURE_2D -> GL11C.GL_TEXTURE_BINDING_2D;
            case GL30C.GL_TEXTURE_2D_ARRAY -> GL30C.GL_TEXTURE_BINDING_2D_ARRAY;
            case GL32C.GL_TEXTURE_2D_MULTISAMPLE -> GL32C.GL_TEXTURE_BINDING_2D_MULTISAMPLE;
            case GL32C.GL_TEXTURE_2D_MULTISAMPLE_ARRAY -> GL32C.GL_TEXTURE_BINDING_2D_MULTISAMPLE_ARRAY;
            case GL13C.GL_TEXTURE_CUBE_MAP -> GL13C.GL_TEXTURE_BINDING_CUBE_MAP;
            default -> GL11C.GL_TEXTURE_BINDING_2D;
        };
    }
}
