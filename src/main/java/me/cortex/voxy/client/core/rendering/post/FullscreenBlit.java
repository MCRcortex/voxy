package me.cortex.voxy.client.core.rendering.post;

import me.cortex.voxy.client.core.gl.GlGraphicsPipeline;
import me.cortex.voxy.client.core.gl.shader.ShaderLoader;
import me.cortex.voxy.client.core.gpu.GraphicsPipelineDesc;
import me.cortex.voxy.client.core.gpu.IGpuPipeline;
import me.cortex.voxy.client.core.gpu.PipelineState;
import me.cortex.voxy.client.core.gpu.RenderBackendFactory;
import me.cortex.voxy.client.core.gpu.VertexLayout;

import java.util.Map;

import static org.lwjgl.opengl.GL11C.GL_RGBA8;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLE_STRIP;
import static org.lwjgl.opengl.GL11C.glDrawArrays;
import static org.lwjgl.opengl.GL15C.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.glBindBufferRange;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glGenVertexArrays;
import static org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL45C.glCreateBuffers;
import static org.lwjgl.opengl.GL45C.glNamedBufferData;
import static org.lwjgl.opengl.GL45C.nglNamedBufferSubData;

/**
 * Helper that compiles a vertex+fragment shader pair, exposes a uniform-push
 * surface, and provides a fullscreen-quad blit primitive.
 *
 * M9 status: the underlying program is now created through
 * {@link me.cortex.voxy.client.core.gpu.RenderBackend#createGraphicsPipeline},
 * so the shaders compile cleanly on Metal/Vulkan as well as OpenGL. The
 * {@link #bind} / {@link #blit} entry points are still raw GL (glUseProgram +
 * glDrawArrays as a TRIANGLE_STRIP); they only run on the OpenGL backend
 * because AbstractRenderPipeline.runPipeline early-returns on Metal/Vulkan
 * until the IOSurface bridge lands. The new {@link #setBytes} method
 * mirrors the encoder API and replaces the old per-location
 * {@code glUniform*} pattern across all four callers — write into a UBO
 * push struct declared in the shader.
 */
public class FullscreenBlit {

    private static final int EMPTY_VAO = glGenVertexArrays();

    private final IGpuPipeline pipeline;
    /** Cached GL program id for the bind path. 0 on non-GL backends. */
    private final int glProgram;

    /** Lazy UBO used to back {@link #setBytes}; created on first use. */
    private int pushUbo;
    private long pushUboCapacity;

    public FullscreenBlit(String fragId) {
        this("voxy:post/fullscreen.vert", fragId, Map.of());
    }

    public FullscreenBlit(String vertId, String fragId) {
        this(vertId, fragId, Map.of());
    }

    /** Backend-agnostic constructor — supply a defines map for shader permutations. */
    public FullscreenBlit(String vertId, String fragId, Map<String, String> defines) {
        // Default pipeline state — callers manage depth/blend/stencil themselves
        // through raw GL because the FullscreenBlit operates inside someone else's
        // framebuffer (sourceFB from MC's render target). On non-GL backends the
        // pipeline still compiles and validates, even though we don't draw.
        GraphicsPipelineDesc desc = new GraphicsPipelineDesc(
                ShaderLoader.parse(vertId),
                ShaderLoader.parse(fragId),
                defines != null ? defines : Map.of(),
                null, null,           // no MSL — runtime compiler produces it on Metal
                null, null,           // no SPIRV — runtime compiler produces it on Vulkan
                GL_RGBA8,             // color format — unused by GL bind()/blit() path
                VertexLayout.EMPTY,   // gl_VertexID-driven full-screen quad
                PipelineState.DEFAULT,// caller sets depth/blend/cull via raw GL
                fragId);
        this.pipeline = RenderBackendFactory.get().createGraphicsPipeline(desc);
        this.glProgram = (this.pipeline instanceof GlGraphicsPipeline gp) ? gp.program() : 0;
    }

    /**
     * Bind the underlying program. Raw glUseProgram — only meaningful on the
     * GL backend (returns silently if running on Metal/Vulkan, where the
     * caller can't reach here anyway given the runPipeline early-return).
     */
    public void bind() {
        if (this.glProgram != 0) {
            glUseProgram(this.glProgram);
        }
    }

    /**
     * Push a small block of bytes into a UBO at the given binding index.
     * Same semantics as {@link me.cortex.voxy.client.core.gpu.RenderEncoder#setBytes}.
     * Replaces the {@code glUniform*(location, ...)} pattern callers used to
     * use against location-based uniforms; the shader now declares a UBO
     * block at the corresponding binding.
     *
     * Currently GL-only — the FullscreenBlit's bind/blit path doesn't run
     * through a {@code RenderEncoder} yet, so we issue the equivalent raw
     * GL calls directly. When IOSurface bridge lands and the callers move
     * onto the encoder, this method becomes a no-op shim or migrates to
     * the encoder's setBytes.
     */
    public void setBytes(int binding, long dataAddr, int dataSize) {
        ensurePushUbo(dataSize);
        nglNamedBufferSubData(this.pushUbo, 0, dataSize, dataAddr);
        glBindBufferRange(GL_UNIFORM_BUFFER, binding, this.pushUbo, 0, dataSize);
    }

    /**
     * Draw a fullscreen TRIANGLE_STRIP (4 vertices, attribute-less — the
     * vertex shader synthesises positions from {@code gl_VertexID}).
     * Caller is responsible for the bound framebuffer and depth/blend/
     * stencil state.
     */
    public void blit() {
        if (this.glProgram == 0) return;
        glBindVertexArray(EMPTY_VAO);
        glUseProgram(this.glProgram);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        glBindVertexArray(0);
    }

    public void delete() {
        if (this.pushUbo != 0) {
            glDeleteBuffers(this.pushUbo);
            this.pushUbo = 0;
        }
        this.pipeline.close();
    }

    /** Direct access to the underlying pipeline, for callers ready to move onto the encoder API. */
    public IGpuPipeline pipeline() {
        return this.pipeline;
    }

    private void ensurePushUbo(int size) {
        long needed = (size + 255) & ~255L;
        if (this.pushUbo == 0) {
            this.pushUbo = glCreateBuffers();
            this.pushUboCapacity = Math.max(needed, 256);
            glNamedBufferData(this.pushUbo, this.pushUboCapacity, GL_DYNAMIC_DRAW);
        } else if (needed > this.pushUboCapacity) {
            this.pushUboCapacity = needed;
            glNamedBufferData(this.pushUbo, this.pushUboCapacity, GL_DYNAMIC_DRAW);
        }
    }
}
