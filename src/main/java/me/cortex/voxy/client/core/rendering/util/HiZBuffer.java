package me.cortex.voxy.client.core.rendering.util;

import me.cortex.voxy.client.core.gl.shader.ShaderLoader;
import me.cortex.voxy.client.core.gpu.GraphicsPipelineDesc;
import me.cortex.voxy.client.core.gpu.IGpuFramebuffer;
import me.cortex.voxy.client.core.gpu.IGpuPipeline;
import me.cortex.voxy.client.core.gpu.IGpuSampler;
import me.cortex.voxy.client.core.gpu.IGpuTexture;
import me.cortex.voxy.client.core.gpu.PipelineState;
import me.cortex.voxy.client.core.gpu.RenderBackend;
import me.cortex.voxy.client.core.gpu.RenderBackendFactory;
import me.cortex.voxy.client.core.gpu.RenderEncoder;
import me.cortex.voxy.client.core.gpu.RenderPassDesc;
import me.cortex.voxy.client.core.gpu.SamplerDesc;
import me.cortex.voxy.client.core.gpu.VertexLayout;

import static org.lwjgl.opengl.GL11C.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL30C.GL_DEPTH24_STENCIL8;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_BASE_LEVEL;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_MAX_LEVEL;
import static me.cortex.voxy.client.core.gl.GLCompat.textureParameteri;

/**
 * Hierarchical-Z buffer — power-of-two depth pyramid sampled by the
 * traversal compute pass for occlusion culling.
 *
 * Build strategy (one per frame): for each mip level i in [0, levels), draw
 * a fullscreen quad with depth-test=ALWAYS into the depth attachment at
 * level i, sampling from the source depth at level (i-1). For i=0 the
 * source is the externally supplied depth texture (vanilla MC's depth
 * target); for i&gt;0 the source is this texture's own previous mip,
 * selected via GL_TEXTURE_BASE_LEVEL/MAX_LEVEL state mutation.
 *
 * M9 status: blit graphics pipeline now flows through the
 * {@link RenderBackend} encoder abstraction — per-mip render pass, draw
 * 4 vertices as TRIANGLE_STRIP (was TRIANGLE_FAN; blit.vsh re-ordered
 * to match). The remaining raw GL calls are
 * {@code glBindTextureUnit} for the source texture (external GL id —
 * no IGpuTexture available; caller side is not yet migrated) and the
 * BASE/MAX_LEVEL mutation that selects the source mip for sampling.
 * Both are sampling-state side effects that GL handles globally; on
 * Metal/Vulkan they will be replaced by per-mip
 * {@code IGpuTexture.createView(level, count)} (the M9 follow-up
 * tracked in M-SERIES-PORT-STATE).
 */
public class HiZBuffer {

    private final RenderBackend backend = RenderBackendFactory.get();
    private final IGpuPipeline blitPipeline;
    private final IGpuSampler sampler = this.backend.createSampler(SamplerDesc.builder()
            .filter(SamplerDesc.Filter.NEAREST, SamplerDesc.Filter.NEAREST)
            .mipFilter(SamplerDesc.MipFilter.NEAREST)
            .wrap(SamplerDesc.Wrap.CLAMP_TO_EDGE, SamplerDesc.Wrap.CLAMP_TO_EDGE)
            .label("hizBlitSampler")
            .build());

    private final IGpuFramebuffer fb = RenderBackendFactory.get().createFramebuffer().name("HiZ");
    private final int type;
    private IGpuTexture texture;
    private int levels;
    private int width;
    private int height;

    public HiZBuffer() {
        this(GL_DEPTH24_STENCIL8);
    }

    public HiZBuffer(int type) {
        this.type = type;
        // Depth-only pass: ALWAYS pass + write depth, no blend, no cull,
        // empty vertex layout (gl_VertexID-driven fullscreen quad).
        PipelineState state = new PipelineState(
                new PipelineState.DepthState(true, true, PipelineState.CompareOp.ALWAYS),
                PipelineState.BlendState.OPAQUE,
                PipelineState.RasterState.NO_CULL);
        this.blitPipeline = this.backend.createGraphicsPipeline(new GraphicsPipelineDesc(
                ShaderLoader.parse("voxy:hiz/blit.vsh"),
                ShaderLoader.parse("voxy:hiz/blit.fsh"),
                null,                       // no defines
                null, null,                  // no MSL
                null, null,                  // no SPIRV
                0,                           // no color format — depth-only pass
                VertexLayout.EMPTY,
                state,
                "HiZBuffer.blit"));
    }

    private void alloc(int width, int height) {
        this.levels = (int) Math.ceil(Math.log(Math.max(width, height)) / Math.log(2));

        this.texture = this.backend.createTexture()
                .store(this.type, this.levels, width, height)
                .name("HiZ");

        this.width = width;
        this.height = height;

        this.fb.bind(GL_DEPTH_ATTACHMENT, this.texture, 0).verify();
    }

    public void buildMipChain(int srcDepthTex, int width, int height) {
        if (this.width != Integer.highestOneBit(width) || this.height != Integer.highestOneBit(height)) {
            if (this.texture != null) {
                this.texture.free();
                this.texture = null;
            }
            this.alloc(Integer.highestOneBit(width), Integer.highestOneBit(height));
        }

        // Pre-bind the external source texture to unit 0 (sampler slot 0). The
        // encoder won't call setTexture(0, ...) inside the pass, so this binding
        // persists across the draw. GL only — Metal/Vulkan replacement is the
        // per-mip createView API tracked in M-SERIES-PORT-STATE.
        org.lwjgl.opengl.GL45C.glBindTextureUnit(0, srcDepthTex);

        int cw = this.width;
        int ch = this.height;
        for (int i = 0; i < this.levels; i++) {
            try (RenderEncoder encoder = this.backend.beginRenderPass(
                    RenderPassDesc.builder(cw, ch)
                            .depthAttachment(this.texture, i,
                                    RenderPassDesc.LoadAction.DONT_CARE,
                                    RenderPassDesc.StoreAction.STORE, 1.0f)
                            .build())) {
                encoder.setPipeline(this.blitPipeline);
                encoder.setSampler(0, this.sampler);
                encoder.setViewport(0, 0, cw, ch, 0, 1);
                encoder.draw(RenderEncoder.PRIMITIVE_TRIANGLE_STRIP, 0, 4, 1, 0);
            }

            cw = Math.max(cw / 2, 1);
            ch = Math.max(ch / 2, 1);

            // After drawing mip i, restrict the texture's sampling range so the
            // next pass reads from level i. GL_TEXTURE_BASE_LEVEL affects
            // sampling only, not FBO attachment (which uses an explicit level),
            // so this is safe even though we'll attach level i+1 next.
            textureParameteri(this.texture.id(), GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, i);
            textureParameteri(this.texture.id(), GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, i);
            if (i == 0) {
                // Switch from external source to self.
                org.lwjgl.opengl.GL45C.glBindTextureUnit(0, this.texture.id());
            }
        }

        // Restore the full sampling range so traversal samples the whole pyramid.
        textureParameteri(this.texture.id(), GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);
        textureParameteri(this.texture.id(), GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 1000);

        // The encoder's close() rebinds FBO 0 and ends the pass.
        // Restore the viewport for the caller's outer pass.
        org.lwjgl.opengl.GL11C.glViewport(0, 0, width, height);
    }

    public void free() {
        this.fb.free();
        if (this.texture != null) {
            this.texture.free();
            this.texture = null;
        }
        this.sampler.close();
        this.blitPipeline.close();
    }

    public int getHizTextureId() {
        return this.texture.id();
    }

    /** Backend-agnostic accessor used by callers migrated onto the encoder API. */
    public IGpuTexture getHizTexture() {
        return this.texture;
    }

    public int getPackedLevels() {
        return (this.width << 16) | this.height;
    }

    // Suppressed-but-still-referenced field aliases so callers that reach in
    // continue to compile after the API tightened. GL_DEPTH_COMPONENT is the
    // sampler swizzle target depth textures still default to; left as a
    // documented constant.
    @SuppressWarnings("unused")
    private static final int LEGACY_DEPTH_FORMAT_HINT = GL_DEPTH_COMPONENT;
}
