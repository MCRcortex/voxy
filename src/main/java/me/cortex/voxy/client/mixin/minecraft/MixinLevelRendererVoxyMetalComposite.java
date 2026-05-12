package me.cortex.voxy.client.mixin.minecraft;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import me.cortex.voxy.client.core.AbstractRenderPipeline;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.gpu.BackendType;
import me.cortex.voxy.client.core.gpu.RenderBackendFactory;
import me.cortex.voxy.client.core.interop.IOSurfaceBridge;
import me.cortex.voxy.client.core.metal.MetalNative;
import me.cortex.voxy.common.Logger;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.opengl.GL11C.GL_LINEAR;
import static org.lwjgl.opengl.GL11C.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.glTexParameteri;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL11C.glDeleteTextures;
import static org.lwjgl.opengl.GL11C.glGenTextures;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;
import static org.lwjgl.opengl.GL30C.glBlitFramebuffer;
import static org.lwjgl.opengl.GL30C.glGenFramebuffers;
import static org.lwjgl.opengl.GL30C.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL32C.glFramebufferTexture;

/**
 * Composites Voxy's Metal-side render output onto MC's framebuffer.
 *
 * Voxy's render pipeline on Metal writes to an {@link IOSurfaceBridge}-backed
 * texture (see {@link AbstractRenderPipeline#metalBridge()}). This mixin
 * runs at the end of MC's {@code LevelRenderer.renderLevel} and blits the
 * shared IOSurface — which is bound to a GL texture via
 * {@code CGLTexImageIOSurface2D} on first use — onto MC's main render
 * target. After this point, MC's normal HUD / particle / GUI rendering
 * continues over the composited image.
 *
 * Active only when:
 *   - Voxy is enabled (VoxyRenderSystem != null on the renderer).
 *   - Backend is non-OpenGL (so the bridge is meaningful).
 *   - The pipeline actually has a metalBridge (set on first
 *     {@code runPipeline} call after the player enters a world).
 *
 * If the demo overlay ({@code -DVOXY_BRIDGE_DEMO=1}) is also active they
 * coexist — the demo blits its own 256x256 corner, this mixin blits the
 * full Voxy bridge underneath.
 */
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRendererVoxyMetalComposite implements IGetVoxyRenderSystem {

    @Unique private int voxy$compositeFbo;
    @Unique private int voxy$compositeGlTex;
    @Unique private long voxy$boundIoSurface;

    @Inject(method = "renderLevel", at = @At("RETURN"), order = 9500)
    private void voxy$compositeMetalBridge(
            GraphicsResourceAllocator allocator,
            DeltaTracker tickCounter,
            boolean renderBlockOutline,
            Camera camera,
            Matrix4f positionMatrix,
            Matrix4f projectionMatrix,
            Matrix4f basicProjectionMatrix,
            GpuBufferSlice fogBuffer,
            Vector4f fogColor,
            boolean renderSky,
            CallbackInfo ci) {
        if (RenderBackendFactory.get().getType() == BackendType.OPENGL) return;
        VoxyRenderSystem vrs = this.getVoxyRenderSystem();
        if (vrs == null) return;
        // The pipeline is not directly accessible; we read the bridge via the
        // pipeline's accessor. Since renderOpaque already called runPipeline
        // by the time we get here, metalBridge() should be non-null.
        AbstractRenderPipeline pipeline = vrs.getPipeline();
        if (pipeline == null) return;
        IOSurfaceBridge bridge = pipeline.metalBridge();
        if (bridge == null) return;

        // Lazy-bind the bridge to a GL texture once per IOSurface instance.
        // If the pipeline reallocates the bridge (on framebuffer resize),
        // the cached bind becomes stale and we redo it.
        if (this.voxy$compositeGlTex == 0 || this.voxy$boundIoSurface != bridge.ioSurfaceHandle()) {
            if (this.voxy$compositeGlTex != 0) {
                glDeleteTextures(this.voxy$compositeGlTex);
                this.voxy$compositeGlTex = 0;
            }
            if (this.voxy$compositeFbo != 0) {
                glDeleteFramebuffers(this.voxy$compositeFbo);
                this.voxy$compositeFbo = 0;
            }
            if (MetalNative.cglGetCurrentContext() == 0) {
                Logger.warn("voxy$compositeMetalBridge: no current CGL context");
                return;
            }
            this.voxy$compositeGlTex = glGenTextures();
            if (this.voxy$compositeGlTex == 0) {
                Logger.error("voxy$compositeMetalBridge: glGenTextures returned 0");
                return;
            }
            int target = 0x84F5 /* GL_TEXTURE_RECTANGLE */;
            glBindTexture(target, this.voxy$compositeGlTex);
            glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(target, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(target, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(target, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            if (!bridge.bindToGlTexture(this.voxy$compositeGlTex)) {
                Logger.error("voxy$compositeMetalBridge: bridge.bindToGlTexture failed");
                glDeleteTextures(this.voxy$compositeGlTex);
                this.voxy$compositeGlTex = 0;
                return;
            }
            this.voxy$boundIoSurface = bridge.ioSurfaceHandle();

            // Allocate the source FBO holding the rectangle texture as
            // GL_COLOR_ATTACHMENT0 — glBlitFramebuffer reads from it.
            this.voxy$compositeFbo = glGenFramebuffers();
            int prevReadFb = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, this.voxy$compositeFbo);
            glFramebufferTexture(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, this.voxy$compositeGlTex, 0);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, prevReadFb);
            Logger.info("voxy$compositeMetalBridge: bridge bound to GL tex " + this.voxy$compositeGlTex
                    + ", composite FBO " + this.voxy$compositeFbo + " ready");
        }

        // Blit the full bridge over MC's currently bound DRAW framebuffer
        // (MC's main render target at this point in renderLevel).
        var mc = Minecraft.getInstance();
        int fbw = mc.getMainRenderTarget().width;
        int fbh = mc.getMainRenderTarget().height;
        int prevReadFb = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, this.voxy$compositeFbo);
        // Flip Y: MTL textures are top-left origin, GL framebuffers bottom-left.
        glBlitFramebuffer(0, 0, fbw, fbh,
                          0, fbh, fbw, 0,
                          GL_COLOR_BUFFER_BIT, GL_LINEAR);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, prevReadFb);
    }
}
