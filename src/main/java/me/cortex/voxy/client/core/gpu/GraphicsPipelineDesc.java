package me.cortex.voxy.client.core.gpu;

/**
 * Descriptor for a graphics pipeline state object.
 *
 * Carries both Metal (MSL strings) and Vulkan (SPIRV bytecode) shader
 * representations so the same {@link RenderBackend#createGraphicsPipeline}
 * call works for both backends — the backend picks whichever it needs and
 * ignores the other. {@link me.cortex.voxy.client.core.gpu.shader.RuntimeShaderCompiler}
 * produces both during a single compile call.
 *
 * The M5 surface is intentionally minimal — single color attachment, no
 * vertex layout (the M5 triangle test uses gl_VertexIndex-driven hardcoded
 * positions). M7+ will add vertex descriptors, blend state, depth/stencil,
 * and multiple color attachments.
 */
public final class GraphicsPipelineDesc {
    public final String vertexMsl;
    public final String fragmentMsl;
    public final byte[] vertexSpirv;
    public final byte[] fragmentSpirv;
    /**
     * Original GLSL source for the vertex stage. Used by the OpenGL backend
     * (compiled via the legacy {@code Shader.Builder}); Metal/Vulkan ignore
     * this and consume the MSL/SPIRV fields. May be null on call sites that
     * only target Metal/Vulkan (smoke tests, M5 triangle path).
     */
    public final String vertexGlsl;
    public final String fragmentGlsl;
    /** Compile-time defines injected ahead of {@code #version}. Voxy's Shader.Builder
     * uses these for per-pipeline permutations; same map is forwarded to
     * RuntimeShaderCompiler on Metal/Vulkan, and to {@code #define} prepends
     * on the GL side. May be null/empty. */
    public final java.util.Map<String, String> defines;
    /** OpenGL-style color format (e.g. GL_RGBA8 = 0x8058). Backends translate. */
    public final int colorAttachmentFormat;
    /** Vertex inputs. Use {@link VertexLayout#EMPTY} for gl_VertexIndex-driven shaders. */
    public final VertexLayout vertexLayout;
    /** Static state baked into the pipeline (depth, blend, raster). */
    public final PipelineState state;
    public final String label;
    /**
     * If true, the pipeline will be referenced from inside an
     * {@code MTLIndirectCommandBuffer} command. Metal's
     * {@code supportIndirectCommandBuffers} flag rejects some
     * shader features (notably certain fragment outputs), so it must be
     * opt-in. Defaults to false; only MDIC's terrain pipelines opt in.
     */
    public final boolean usedInIndirectCommandBuffer;

    public GraphicsPipelineDesc(String vertexMsl, String fragmentMsl,
                                byte[] vertexSpirv, byte[] fragmentSpirv,
                                int colorAttachmentFormat,
                                VertexLayout vertexLayout,
                                PipelineState state,
                                String label) {
        this(null, null, null,
                vertexMsl, fragmentMsl, vertexSpirv, fragmentSpirv,
                colorAttachmentFormat, vertexLayout, state, label);
    }

    /** Full constructor for M9 migration call sites that have GLSL source. */
    public GraphicsPipelineDesc(String vertexGlsl, String fragmentGlsl,
                                java.util.Map<String, String> defines,
                                String vertexMsl, String fragmentMsl,
                                byte[] vertexSpirv, byte[] fragmentSpirv,
                                int colorAttachmentFormat,
                                VertexLayout vertexLayout,
                                PipelineState state,
                                String label) {
        this.vertexGlsl = vertexGlsl;
        this.fragmentGlsl = fragmentGlsl;
        this.defines = defines != null ? defines : java.util.Map.of();
        this.vertexMsl = vertexMsl;
        this.fragmentMsl = fragmentMsl;
        this.vertexSpirv = vertexSpirv;
        this.fragmentSpirv = fragmentSpirv;
        this.colorAttachmentFormat = colorAttachmentFormat;
        this.vertexLayout = vertexLayout != null ? vertexLayout : VertexLayout.EMPTY;
        this.state = state != null ? state : PipelineState.DEFAULT;
        this.label = label;
        this.usedInIndirectCommandBuffer = false;
    }

    /** Internal constructor for callers that need to opt the pipeline into ICB usage. */
    GraphicsPipelineDesc(String vertexGlsl, String fragmentGlsl,
                         java.util.Map<String, String> defines,
                         String vertexMsl, String fragmentMsl,
                         byte[] vertexSpirv, byte[] fragmentSpirv,
                         int colorAttachmentFormat,
                         VertexLayout vertexLayout,
                         PipelineState state,
                         String label,
                         boolean usedInIndirectCommandBuffer) {
        this.vertexGlsl = vertexGlsl;
        this.fragmentGlsl = fragmentGlsl;
        this.defines = defines != null ? defines : java.util.Map.of();
        this.vertexMsl = vertexMsl;
        this.fragmentMsl = fragmentMsl;
        this.vertexSpirv = vertexSpirv;
        this.fragmentSpirv = fragmentSpirv;
        this.colorAttachmentFormat = colorAttachmentFormat;
        this.vertexLayout = vertexLayout != null ? vertexLayout : VertexLayout.EMPTY;
        this.state = state != null ? state : PipelineState.DEFAULT;
        this.label = label;
        this.usedInIndirectCommandBuffer = usedInIndirectCommandBuffer;
    }

    /** Returns a copy of this desc with usedInIndirectCommandBuffer set. */
    public GraphicsPipelineDesc withIndirectCommandBufferUsage(boolean used) {
        return new GraphicsPipelineDesc(this.vertexGlsl, this.fragmentGlsl, this.defines,
                this.vertexMsl, this.fragmentMsl, this.vertexSpirv, this.fragmentSpirv,
                this.colorAttachmentFormat, this.vertexLayout, this.state, this.label, used);
    }

    /** Backward-compat overload without explicit pipeline state (uses DEFAULT). */
    public GraphicsPipelineDesc(String vertexMsl, String fragmentMsl,
                                byte[] vertexSpirv, byte[] fragmentSpirv,
                                int colorAttachmentFormat,
                                VertexLayout vertexLayout, String label) {
        this(vertexMsl, fragmentMsl, vertexSpirv, fragmentSpirv,
             colorAttachmentFormat, vertexLayout, PipelineState.DEFAULT, label);
    }

    /** Convenience overload for shaders without vertex inputs. */
    public GraphicsPipelineDesc(String vertexMsl, String fragmentMsl,
                                byte[] vertexSpirv, byte[] fragmentSpirv,
                                int colorAttachmentFormat, String label) {
        this(vertexMsl, fragmentMsl, vertexSpirv, fragmentSpirv,
             colorAttachmentFormat, VertexLayout.EMPTY, PipelineState.DEFAULT, label);
    }
}
