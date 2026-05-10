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

    /** 16-bit index buffer (matches GL_UNSIGNED_SHORT / VK_INDEX_TYPE_UINT16). */
    int INDEX_TYPE_UINT16 = 0;
    /** 32-bit index buffer (matches GL_UNSIGNED_INT / VK_INDEX_TYPE_UINT32). */
    int INDEX_TYPE_UINT32 = 1;

    /**
     * Bind a graphics pipeline state object. All subsequent draws use this
     * pipeline until another setPipeline call.
     */
    void setPipeline(IGpuPipeline pipeline);

    /**
     * Bind a buffer at the given binding index, accessible from both the
     * vertex and fragment stages. Voxy's GLSL uses
     * {@code layout(binding = N) buffer ...} or {@code uniform Block { ... }}
     * blocks that read from both stages; backends bind to the shared slot
     * appropriately (Metal: setVertexBuffer + setFragmentBuffer; Vulkan: a
     * descriptor write at set 0 with stage flags VERTEX|FRAGMENT; GL: SSBO
     * via glBindBufferBase or UBO via glBindBufferRange).
     */
    void setBuffer(int binding, IGpuBuffer buffer, long offset);

    /**
     * Bind a sampled or storage texture at the given binding index, again
     * accessible from both vertex and fragment stages.
     */
    void setTexture(int binding, IGpuTexture texture);

    /**
     * Bind a buffer at the given vertex-input slot (the slot defined by the
     * pipeline's vertex layout). Distinct from {@link #setBuffer} — this
     * feeds the rasterizer's per-vertex attribute fetch, not a shader-side
     * SSBO/UBO.
     */
    void bindVertexBuffer(int slot, IGpuBuffer buffer, long offset);

    /** Bind the active index buffer for {@link #drawIndexed}. */
    void bindIndexBuffer(IGpuBuffer buffer, int indexType, long offset);

    /**
     * Set the viewport. Origin is top-left in pixel coordinates; minDepth and
     * maxDepth bound the gl_Position.z range that maps to [0, 1] in the
     * depth buffer. Most callers use (0, 0, fbWidth, fbHeight, 0, 1).
     */
    void setViewport(float x, float y, float width, float height, float minDepth, float maxDepth);

    /** Set the scissor rectangle in pixel coordinates (origin top-left). */
    void setScissor(int x, int y, int width, int height);

    /**
     * Issue a non-indexed draw. firstVertex/vertexCount feed gl_VertexIndex;
     * instanceCount/baseInstance feed gl_InstanceIndex.
     */
    void draw(int primitiveType, int firstVertex, int vertexCount,
              int instanceCount, int baseInstance);

    /**
     * Issue an indexed draw. The index buffer must have been bound via
     * {@link #bindIndexBuffer}. vertexOffset is added to each fetched index
     * before vertex fetch. firstInstance feeds gl_InstanceIndex - baseInstance.
     */
    void drawIndexed(int primitiveType, int indexCount, int instanceCount,
                     int firstIndex, int vertexOffset, int firstInstance);

    @Override
    void close();
}
