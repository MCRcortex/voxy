package me.cortex.voxy.client.core.interop;

import me.cortex.voxy.client.core.metal.MetalNative;
import me.cortex.voxy.common.Logger;
import net.minecraft.client.Minecraft;

import static org.lwjgl.opengl.GL11C.GL_LINEAR;
import static org.lwjgl.opengl.GL11C.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL11C.glTexParameteri;
import static org.lwjgl.opengl.GL11C.glDeleteTextures;
import static org.lwjgl.opengl.GL11C.glGenTextures;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;
import static org.lwjgl.opengl.GL30C.glBlitFramebuffer;
import static org.lwjgl.opengl.GL30C.glCheckFramebufferStatus;
import static org.lwjgl.opengl.GL30C.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30C.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30C.glGenFramebuffers;

/**
 * Composites a Voxy IOSurfaceBridge's contents into the currently bound
 * GL_DRAW_FRAMEBUFFER. Lazy-binds the IOSurface to a GL_TEXTURE_RECTANGLE
 * once (via {@code CGLTexImageIOSurface2D}) and reuses a transient source
 * FBO with that texture as COLOR_ATTACHMENT0.
 *
 * Caller responsibility: must invoke from a code path where MC's main
 * render target FBO is the active GL_DRAW_FRAMEBUFFER. Verified call
 * sites are inside Sodium's chunk render (mixin into
 * {@code DefaultChunkRenderer.render} BEFORE its end() call) — there MC
 * has bound the main RT for chunk drawing. Calling from
 * {@code LevelRenderer.renderLevel}'s RETURN doesn't work because by
 * then MC has already unbound to FBO 0, so a blit there ends up in the
 * window backbuffer (which MC then overwrites with its own RT→window
 * blit, hiding our output).
 */
public final class IOSurfaceBridgeCompositor {

    private static int compositeFbo;
    private static int compositeGlTex;
    private static long boundIoSurface;
    private static int blitFrameCounter;
    private static boolean disabled;

    private IOSurfaceBridgeCompositor() {}

    /** Composite the bridge's contents into the currently bound DRAW framebuffer. */
    public static void composite(IOSurfaceBridge bridge) {
        if (disabled || bridge == null || bridge.ioSurfaceHandle() == 0) return;

        // (Re)bind on first use or after the bridge re-allocated (resize).
        if (compositeGlTex == 0 || boundIoSurface != bridge.ioSurfaceHandle()) {
            if (!rebind(bridge)) {
                disabled = true;
                return;
            }
        }

        var mc = Minecraft.getInstance();
        int fbw = mc.getMainRenderTarget().width;
        int fbh = mc.getMainRenderTarget().height;

        int prevReadFb = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int currentDrawFb = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);

        glBindFramebuffer(GL_READ_FRAMEBUFFER, compositeFbo);
        // M11 diagnostic: blit only the LEFT QUARTER of the bridge into the
        // left quarter of MC's RT, leaving the rest of MC's image (Sodium
        // chunk terrain) untouched. This proves the bridge→MC pipe works
        // while keeping Sodium's output visible for verification.
        // Y-flip: Metal textures are top-left origin, GL framebuffers bottom-left.
        int dstW = fbw / 4;
        glBlitFramebuffer(0, 0, dstW, fbh,
                          0, fbh, dstW, 0,
                          GL_COLOR_BUFFER_BIT, GL_LINEAR);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, prevReadFb);

        blitFrameCounter++;
        if ((blitFrameCounter % 600) == 1) {
            Logger.info("IOSurfaceBridgeCompositor: blit fired (left " + dstW + "px), DRAW_FBO="
                    + currentDrawFb + " size " + fbw + "x" + fbh + " frame=" + blitFrameCounter);
        }
    }

    private static boolean rebind(IOSurfaceBridge bridge) {
        if (compositeGlTex != 0) {
            glDeleteTextures(compositeGlTex);
            compositeGlTex = 0;
        }
        if (compositeFbo != 0) {
            glDeleteFramebuffers(compositeFbo);
            compositeFbo = 0;
        }
        if (MetalNative.cglGetCurrentContext() == 0) {
            Logger.warn("IOSurfaceBridgeCompositor: no current CGL context — composite disabled");
            return false;
        }
        compositeGlTex = glGenTextures();
        if (compositeGlTex == 0) {
            Logger.error("IOSurfaceBridgeCompositor: glGenTextures returned 0");
            return false;
        }
        int rectTarget = 0x84F5 /* GL_TEXTURE_RECTANGLE */;
        glBindTexture(rectTarget, compositeGlTex);
        glTexParameteri(rectTarget, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(rectTarget, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(rectTarget, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(rectTarget, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        if (!bridge.bindToGlTexture(compositeGlTex)) {
            Logger.error("IOSurfaceBridgeCompositor: bindToGlTexture failed");
            glDeleteTextures(compositeGlTex);
            compositeGlTex = 0;
            return false;
        }
        boundIoSurface = bridge.ioSurfaceHandle();

        compositeFbo = glGenFramebuffers();
        int prevReadFb = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, compositeFbo);
        glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                rectTarget, compositeGlTex, 0);
        int status = glCheckFramebufferStatus(GL_READ_FRAMEBUFFER);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, prevReadFb);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            Logger.error("IOSurfaceBridgeCompositor: source FBO incomplete (status=0x"
                    + Integer.toHexString(status) + ")");
            glDeleteFramebuffers(compositeFbo);
            compositeFbo = 0;
            glDeleteTextures(compositeGlTex);
            compositeGlTex = 0;
            return false;
        }

        Logger.info("IOSurfaceBridgeCompositor: bridge bound to GL tex " + compositeGlTex
                + ", composite FBO " + compositeFbo + " (status=COMPLETE), ready");
        return true;
    }
}
