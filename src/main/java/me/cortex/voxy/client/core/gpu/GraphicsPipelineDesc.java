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
    /** OpenGL-style color format (e.g. GL_RGBA8 = 0x8058). Backends translate. */
    public final int colorAttachmentFormat;
    public final String label;

    public GraphicsPipelineDesc(String vertexMsl, String fragmentMsl,
                                byte[] vertexSpirv, byte[] fragmentSpirv,
                                int colorAttachmentFormat, String label) {
        this.vertexMsl = vertexMsl;
        this.fragmentMsl = fragmentMsl;
        this.vertexSpirv = vertexSpirv;
        this.fragmentSpirv = fragmentSpirv;
        this.colorAttachmentFormat = colorAttachmentFormat;
        this.label = label;
    }
}
