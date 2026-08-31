package me.cortex.voxy.client.core;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.gl.GlFramebuffer;
import me.cortex.voxy.client.core.gl.GlTexture;
import me.cortex.voxy.client.core.rendering.Viewport;
import me.cortex.voxy.client.core.rendering.hierachical.AsyncNodeManager;
import me.cortex.voxy.client.core.rendering.hierachical.HierarchicalOcclusionTraverser;
import me.cortex.voxy.client.core.rendering.hierachical.NodeCleaner;
import me.cortex.voxy.client.core.rendering.post.FullscreenBlit;
import me.cortex.voxy.client.core.util.GPUTiming;
import org.joml.Matrix4f;

import java.util.List;
import java.util.function.BooleanSupplier;

import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL43.GL_DEPTH_STENCIL_TEXTURE_MODE;
import static org.lwjgl.opengl.GL45C.glBindTextureUnit;
import static org.lwjgl.opengl.GL45C.glTextureParameterf;

public class NormalRenderPipeline extends AbstractRenderPipeline {
    private GlTexture colourTex;
    private GlTexture colourSSAOTex;
    private final GlFramebuffer fbSSAO = new GlFramebuffer();

    private final FogMode fogMode;
    private final FullscreenBlit finalBlit;

    private final SSAO ssao;

    public enum FogMode {
        FOG_AND_FADE(false, true, true),
        FOG(false, true, false),
        FADE(true, false, true),
        OFF(true, false, false);
        public final boolean removesVanillaEnvFog;
        public final boolean hasFog;
        public final boolean hasFade;

        FogMode(boolean removesVanillaEnvFog, boolean hasFog, boolean hasFade) {
            this.removesVanillaEnvFog = removesVanillaEnvFog;
            this.hasFog = hasFog;
            this.hasFade = hasFade;
        }
    }

    protected NormalRenderPipeline(RenderProperties properties, AsyncNodeManager nodeManager, NodeCleaner nodeCleaner, HierarchicalOcclusionTraverser traversal, BooleanSupplier frexSupplier) {
        super(properties, nodeManager, nodeCleaner, traversal, frexSupplier, false);
        this.fogMode = VoxyConfig.CONFIG.getFogMode();
        this.finalBlit = new FullscreenBlit(properties, "voxy:post/blit_texture_depth_cutout.frag",
                a->a.defineIf("HAS_FOG", this.fogMode.hasFog)
                        .defineIf("HAS_FADE", this.fogMode.hasFade).define("EMIT_COLOUR"));


        this.ssao = SSAO.createSSAO(properties, VoxyConfig.CONFIG.getSSAOMode());
    }

    @Override
    protected int setup(Viewport<?> viewport, int sourceDepthTex, int srcWidth, int srcHeight) {
        if (this.colourTex == null || this.colourTex.getHeight() != viewport.height || this.colourTex.getWidth() != viewport.width) {
            if (this.colourTex != null) {
                this.colourTex.free();
                this.colourSSAOTex.free();
            }
            this.fb.resize(viewport.width, viewport.height);

            this.colourTex = new GlTexture().store(GL_RGBA8, 1, viewport.width, viewport.height);
            this.colourSSAOTex = new GlTexture().store(GL_RGBA8, 1, viewport.width, viewport.height);

            this.fb.framebuffer.bind(GL_COLOR_ATTACHMENT0, this.colourTex).verify();
            this.fbSSAO.bind(this.fb.getDepthAttachmentType(), this.fb.getDepthTex()).bind(GL_COLOR_ATTACHMENT0, this.colourSSAOTex).verify();


            glTextureParameterf(this.colourTex.id, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTextureParameterf(this.colourTex.id, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTextureParameterf(this.colourSSAOTex.id, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTextureParameterf(this.colourSSAOTex.id, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTextureParameterf(this.fb.getDepthTex().id, GL_DEPTH_STENCIL_TEXTURE_MODE, GL_DEPTH_COMPONENT);
        }

        this.initDepthStencil(viewport, sourceDepthTex, this.fb.framebuffer.id, srcWidth, srcHeight, viewport.width, viewport.height);
        return this.fb.getDepthTex().id;
    }

    @Override
    protected void postOpaquePreTranslucent(Viewport<?> viewport, int sourceDepthTexture) {
        GPUTiming.INSTANCE.marker("ao");
        this.ssao.computeSSAO(viewport, this.colourSSAOTex, this.colourTex, this.fb.getDepthTex(), sourceDepthTexture);
        glBindFramebuffer(GL_FRAMEBUFFER, this.fbSSAO.id);
    }

    @Override
    protected void finish(Viewport<?> viewport, int sourceDepthTexture, int outputFramebuffer, int srcWidth, int srcHeight) {
        this.finalBlit.bind();
        boolean fogCoversAllRendering = viewport.fogParameters.environmentalEnd()<VoxyRenderSystem.getVanillaRenderDistance();

        if (this.fogMode.hasFog) {
            float start = viewport.fogParameters.environmentalStart();
            float end = viewport.fogParameters.environmentalEnd();
            if (Math.abs(end-start)>1) {
                float invEndFogDelta = 1f / (end - start);
                float endDistance = Math.max(VoxyRenderSystem.getVanillaRenderDistance(), 20*16);//TODO: make this constant a config option
                endDistance *= (float)Math.sqrt(3);
                float startDelta = -start * invEndFogDelta;
                glUniform4f(4, invEndFogDelta, startDelta, Math.clamp(endDistance*invEndFogDelta+startDelta, 0, 1),0);//
                glUniform4f(5, viewport.fogParameters.red(), viewport.fogParameters.green(), viewport.fogParameters.blue(), viewport.fogParameters.alpha());
            } else {
                glUniform4f(4, 0, 0, 0, 0);
                glUniform4f(5, 0, 0, 0, 0);
            }
        }
        if (this.fogMode.hasFade) {
            //TODO: this should be a compile time define
            int MODE = 1;//0:off, 1:xz, 2:xyz
            float rd = VoxyConfig.CONFIG.sectionRenderDistance*16*32 - (float)Math.sqrt(MODE>1?32*32*32:32*32);
            float vanillaRd = VoxyRenderSystem.getVanillaRenderDistance();
            float start = Math.max(vanillaRd, rd*0.9f);//start at 90% of the render distance (10% fade distance)
            float end = Math.max(vanillaRd, rd);

            float scale = 1.0f/(end-start);
            glUniform4f(6, MODE, (-start)*scale, scale, 0);
        }

        glBindTextureUnit(3, this.colourSSAOTex.id);

        //Do alpha blending
        //Unbelievably jank hack, only blit out to the framebuffer if we are rendering fog
        if (!fogCoversAllRendering) {
            glEnable(GL_BLEND);
            glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            AbstractRenderPipeline.transformBlitDepth(this.finalBlit, this.fb.getDepthTex().id, outputFramebuffer, viewport, new Matrix4f(viewport.vanillaProjection).mul(viewport.modelView));
            glDisable(GL_BLEND);
        } else {
            glDisable(GL_STENCIL_TEST);
            glDisable(GL_DEPTH_TEST);
        }
        //glBlitNamedFramebuffer(this.fbSSAO.id, sourceFrameBuffer, 0,0, viewport.width, viewport.height, 0,0, viewport.width, viewport.height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
    }

    @Override
    public void setupAndBindOpaque(Viewport<?> viewport) {
        this.fb.bind();
    }

    @Override
    public void setupAndBindTranslucent(Viewport<?> viewport) {
        glBindFramebuffer(GL_FRAMEBUFFER, this.fbSSAO.id);
    }

    @Override
    public void free() {
        this.finalBlit.delete();
        this.ssao.free();
        this.fbSSAO.free();
        if (this.colourTex != null) {
            this.colourTex.free();
            this.colourSSAOTex.free();
        }
        super.free0();
    }

    @Override
    public void addDebug(List<String> debug) {
        super.addDebug(debug);
        this.ssao.addDebugInfo(debug);
    }
}
