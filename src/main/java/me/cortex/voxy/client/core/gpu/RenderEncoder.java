package me.cortex.voxy.client.core.gpu;

/**
 * In-flight encoder for a render pass.
 *
 * Created by {@link RenderBackend#beginRenderPass(RenderPassDesc)} and closed
 * via {@link #close()} (which ends encoding on Metal/Vulkan and unbinds the
 * framebuffer on OpenGL).
 *
 * M5 surface adds {@link #setPipeline} and {@link #draw} for the triangle
 * smoke test — no resource binding yet (the M5 vertex shader generates
 * positions from {@code gl_VertexIndex}). Resource binding (vertex/index
 * buffers, textures, samplers, push constants) lands in M7+.
 */
public interface RenderEncoder extends AutoCloseable {

    /** Triangle list — 3 vertices per primitive. */
    int PRIMITIVE_TRIANGLES = 0;
    /** Triangle strip — N+2 vertices for N triangles. */
    int PRIMITIVE_TRIANGLE_STRIP = 1;
    /** Line list — 2 vertices per line. */
    int PRIMITIVE_LINES = 2;
    /** Point list — 1 vertex per point. */
    int PRIMITIVE_POINTS = 3;

    /**
     * Bind a graphics pipeline state object. All subsequent draws use this
     * pipeline until another setPipeline call.
     */
    void setPipeline(IGpuPipeline pipeline);

    /**
     * Issue a non-indexed draw. firstVertex/vertexCount feed gl_VertexIndex;
     * instanceCount/baseInstance feed gl_InstanceIndex.
     */
    void draw(int primitiveType, int firstVertex, int vertexCount,
              int instanceCount, int baseInstance);

    @Override
    void close();
}
