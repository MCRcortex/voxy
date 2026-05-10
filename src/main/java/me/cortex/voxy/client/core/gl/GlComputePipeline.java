package me.cortex.voxy.client.core.gl;

import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.gpu.ComputePipelineDesc;
import me.cortex.voxy.client.core.gpu.IGpuPipeline;

import static org.lwjgl.opengl.GL20C.glDeleteProgram;

/**
 * OpenGL implementation of {@link IGpuPipeline} for compute pipelines.
 *
 * Wraps a single-stage {@link Shader} program built from the GLSL compute
 * source carried by {@link ComputePipelineDesc}. Local thread-group size
 * stays encoded in the GLSL ({@code layout(local_size_x=...)}).
 */
public final class GlComputePipeline implements IGpuPipeline {

    private final int program;
    private boolean closed;

    public GlComputePipeline(ComputePipelineDesc desc) {
        if (desc.computeGlsl == null) {
            throw new IllegalArgumentException(
                    "GlComputePipeline requires GLSL source on the ComputePipelineDesc — "
                            + "Voxy's GL backend can't compile MSL/SPIRV. label=" + desc.label);
        }
        Shader.Builder<Shader> builder = Shader.make();
        if (desc.defines != null) {
            for (var entry : desc.defines.entrySet()) {
                builder.define(entry.getKey(), entry.getValue());
            }
        }
        builder.addSource(ShaderType.COMPUTE, desc.computeGlsl);
        Shader shader = builder.compile();
        this.program = shader.id();

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
