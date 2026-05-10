package me.cortex.voxy.client.core.gl;

import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.gpu.GraphicsPipelineDesc;
import me.cortex.voxy.client.core.gpu.IGpuPipeline;
import me.cortex.voxy.client.core.gpu.PipelineState;
import me.cortex.voxy.client.core.gpu.VertexLayout;

import static org.lwjgl.opengl.GL20C.glDeleteProgram;

/**
 * OpenGL implementation of {@link IGpuPipeline} for graphics pipelines.
 *
 * Wraps a {@link Shader} program built from the raw GLSL carried by
 * {@link GraphicsPipelineDesc}. Holds the {@link VertexLayout} and
 * {@link PipelineState} so the encoder can apply them at bind time
 * (Metal/Vulkan bake the state into the PSO; GL needs per-bind setters).
 */
public final class GlGraphicsPipeline implements IGpuPipeline {

    private final int program;
    final VertexLayout vertexLayout;
    final PipelineState state;
    private boolean closed;

    public GlGraphicsPipeline(GraphicsPipelineDesc desc) {
        if (desc.vertexGlsl == null || desc.fragmentGlsl == null) {
            throw new IllegalArgumentException(
                    "GlGraphicsPipeline requires GLSL source on the GraphicsPipelineDesc — "
                            + "Voxy's GL backend can't compile MSL/SPIRV. label=" + desc.label);
        }
        Shader.Builder<Shader> builder = Shader.make();
        if (desc.defines != null) {
            for (var entry : desc.defines.entrySet()) {
                builder.define(entry.getKey(), entry.getValue());
            }
        }
        builder.addSource(ShaderType.VERTEX, desc.vertexGlsl);
        builder.addSource(ShaderType.FRAGMENT, desc.fragmentGlsl);
        Shader shader = builder.compile();
        this.program = shader.id();
        this.vertexLayout = desc.vertexLayout != null ? desc.vertexLayout : VertexLayout.EMPTY;
        this.state = desc.state != null ? desc.state : PipelineState.DEFAULT;

        if (desc.label != null && GlDebug.GL_DEBUG && this.program != 0) {
            org.lwjgl.opengl.GL43C.glObjectLabel(org.lwjgl.opengl.GL43C.GL_PROGRAM,
                    this.program, desc.label);
        }
    }

    public int program() {
        return this.program;
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        if (this.program != 0) {
            glDeleteProgram(this.program);
        }
    }
}
