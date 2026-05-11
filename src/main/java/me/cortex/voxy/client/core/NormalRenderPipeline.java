package me.cortex.voxy.client.core;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.gpu.IGpuFramebuffer;
import me.cortex.voxy.client.core.gpu.IGpuTexture;
import me.cortex.voxy.client.core.gpu.RenderBackendFactory;
import me.cortex.voxy.client.core.rendering.Viewport;
import me.cortex.voxy.client.core.rendering.hierachical.AsyncNodeManager;
import me.cortex.voxy.client.core.rendering.hierachical.HierarchicalOcclusionTraverser;
import me.cortex.voxy.client.core.rendering.hierachical.NodeCleaner;
import me.cortex.voxy.client.core.rendering.post.FullscreenBlit;
import me.cortex.voxy.client.core.rendering.util.DepthFramebuffer;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.util.function.BooleanSupplier;

import static org.lwjgl.opengl.ARBComputeShader.glDispatchCompute;
import static org.lwjgl.opengl.ARBShaderImageLoadStore.glBindImageTexture;
import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_RGBA8;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL14.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL15.GL_READ_WRITE;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL43.GL_DEPTH_STENCIL_TEXTURE_MODE;
import static me.cortex.voxy.client.core.gl.GLCompat.bindTextureUnit;
import static me.cortex.voxy.client.core.gl.GLCompat.textureParameterf;

public class NormalRenderPipeline extends AbstractRenderPipeline {
    private IGpuTexture colourTex;
    private IGpuTexture colourSSAOTex;
    private final IGpuFramebuffer fbSSAO = RenderBackendFactory.get().createFramebuffer();

    private final boolean useEnvFog;
    private final FullscreenBlit finalBlit;

    private final Shader ssaoCompute = Shader.make()
            .add(ShaderType.COMPUTE, "voxy:post/ssao.comp")
            .compile();

    protected NormalRenderPipeline(AsyncNodeManager nodeManager, NodeCleaner nodeCleaner, HierarchicalOcclusionTraverser traversal, BooleanSupplier frexSupplier) {
        super(nodeManager, nodeCleaner, traversal, frexSupplier, false);
        this.useEnvFog = VoxyConfig.CONFIG.useEnvironmentalFog;
        // M9 migration: defines now flow through a Map<String,String> so the
        // backend-agnostic GraphicsPipelineDesc can forward them to GL,
        // Metal, and Vulkan compile paths uniformly.
        java.util.Map<String, String> defines = new java.util.LinkedHashMap<>();
        defines.put("EMIT_COLOUR", "");
        if (this.useEnvFog) defines.put("USE_ENV_FOG", "");
        this.finalBlit = new FullscreenBlit("voxy:post/fullscreen.vert",
                "voxy:post/blit_texture_depth_cutout.frag", defines);
    }

    @Override
    protected int setup(Viewport<?> viewport, int sourceFB, int srcWidth, int srcHeight) {
        if (this.colourTex == null || this.colourTex.getHeight() != viewport.height || this.colourTex.getWidth() != viewport.width) {
            if (this.colourTex != null) {
                this.colourTex.free();
                this.colourSSAOTex.free();
            }
            this.fb.resize(viewport.width, viewport.height);

            this.colourTex = RenderBackendFactory.get().createTexture().store(GL_RGBA8, 1, viewport.width, viewport.height);
            this.colourSSAOTex = RenderBackendFactory.get().createTexture().store(GL_RGBA8, 1, viewport.width, viewport.height);

            this.fb.framebuffer.bind(GL_COLOR_ATTACHMENT0, this.colourTex).verify();
            this.fbSSAO.bind(this.fb.getDepthAttachmentType(), this.fb.getDepthTex()).bind(GL_COLOR_ATTACHMENT0, this.colourSSAOTex).verify();


            textureParameterf(this.colourTex.id(), GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            textureParameterf(this.colourTex.id(), GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            textureParameterf(this.colourSSAOTex.id(), GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            textureParameterf(this.colourSSAOTex.id(), GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            textureParameterf(this.fb.getDepthTex().id(), GL_TEXTURE_2D, GL_DEPTH_STENCIL_TEXTURE_MODE, GL_DEPTH_COMPONENT);
        }

        this.initDepthStencil(sourceFB, this.fb.framebuffer.id(), viewport.width, viewport.height, viewport.width, viewport.height);

        return this.fb.getDepthTex().id();
    }

    @Override
    protected void postOpaquePreTranslucent(Viewport<?> viewport) {
        this.ssaoCompute.bind();
        try (var stack = MemoryStack.stackPush()) {
            long ptr = stack.nmalloc(4*4*4);
            viewport.MVP.getToAddress(ptr);
            nglUniformMatrix4fv(3, 1, false, ptr);//MVP
            viewport.MVP.invert(new Matrix4f()).getToAddress(ptr);
            nglUniformMatrix4fv(4, 1, false, ptr);//invMVP
        }


        glBindImageTexture(0, this.colourSSAOTex.id(), 0, false,0, GL_READ_WRITE, GL_RGBA8);
        bindTextureUnit(1, GL_TEXTURE_2D, this.fb.getDepthTex().id());
        bindTextureUnit(2, GL_TEXTURE_2D, this.colourTex.id());

        glDispatchCompute((viewport.width+31)/32, (viewport.height+31)/32, 1);

        glBindFramebuffer(GL_FRAMEBUFFER, this.fbSSAO.id());
    }

    /** Matches PushFog layout in blit_texture_depth_cutout.frag — two vec4. */
    private static final int PUSH_FOG_BINDING = 15;
    private static final int FOG_PUSH_SIZE = 4 * 4 * 2;

    @Override
    protected void finish(Viewport<?> viewport, int sourceFrameBuffer, int srcWidth, int srcHeight) {
        this.finalBlit.bind();
        if (this.useEnvFog) {
            float start = viewport.fogParameters.environmentalStart();
            float end = viewport.fogParameters.environmentalEnd();
            try (var stack = MemoryStack.stackPush()) {
                long addr = stack.nmalloc(FOG_PUSH_SIZE);
                if (Math.abs(end - start) > 1) {
                    float invEndFogDelta = 1f / (end - start);
                    float endDistance = Math.max(Minecraft.getInstance().gameRenderer.getRenderDistance(), 20 * 16);//TODO: make this constant a config option
                    endDistance *= (float) Math.sqrt(3);
                    float startDelta = -start * invEndFogDelta;
                    // endParams vec4
                    org.lwjgl.system.MemoryUtil.memPutFloat(addr +  0, invEndFogDelta);
                    org.lwjgl.system.MemoryUtil.memPutFloat(addr +  4, startDelta);
                    org.lwjgl.system.MemoryUtil.memPutFloat(addr +  8, Math.clamp(endDistance * invEndFogDelta + startDelta, 0f, 1f));
                    org.lwjgl.system.MemoryUtil.memPutFloat(addr + 12, 0f);
                    // fogColour vec4
                    org.lwjgl.system.MemoryUtil.memPutFloat(addr + 16, viewport.fogParameters.red());
                    org.lwjgl.system.MemoryUtil.memPutFloat(addr + 20, viewport.fogParameters.green());
                    org.lwjgl.system.MemoryUtil.memPutFloat(addr + 24, viewport.fogParameters.blue());
                    org.lwjgl.system.MemoryUtil.memPutFloat(addr + 28, viewport.fogParameters.alpha());
                } else {
                    org.lwjgl.system.MemoryUtil.memSet(addr, 0, FOG_PUSH_SIZE);
                }
                this.finalBlit.setBytes(PUSH_FOG_BINDING, addr, FOG_PUSH_SIZE);
            }
        }

        bindTextureUnit(3, GL_TEXTURE_2D, this.colourSSAOTex.id());

        //Do alpha blending

        glEnable(GL_BLEND);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        AbstractRenderPipeline.transformBlitDepth(this.finalBlit, this.fb.getDepthTex().id(), sourceFrameBuffer, viewport, new Matrix4f(viewport.vanillaProjection).mul(viewport.modelView));
        glDisable(GL_BLEND);
        //glBlitNamedFramebuffer(this.fbSSAO.id, sourceFrameBuffer, 0,0, viewport.width, viewport.height, 0,0, viewport.width, viewport.height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
    }

    @Override
    public void setupAndBindOpaque(Viewport<?> viewport) {
        this.fb.bind();
    }

    @Override
    public void setupAndBindTranslucent(Viewport<?> viewport) {
        glBindFramebuffer(GL_FRAMEBUFFER, this.fbSSAO.id());
    }

    @Override
    public void free() {
        this.finalBlit.delete();
        this.ssaoCompute.free();
        this.fbSSAO.free();
        if (this.colourTex != null) {
            this.colourTex.free();
            this.colourSSAOTex.free();
        }
        super.free0();
    }
}
