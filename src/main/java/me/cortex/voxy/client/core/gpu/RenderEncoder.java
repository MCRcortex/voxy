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
     * Bind a sampler state object at the given binding index. Voxy GLSL
     * uses {@code layout(binding=N) uniform sampler2D ...}; this method
     * pairs with {@link #setTexture} to fill the same binding's
     * sampler-side slot. Available to both vertex and fragment stages.
     */
    void setSampler(int binding, IGpuSampler sampler);

    /**
     * Push a small block of bytes inline as a uniform buffer at the given
     * binding index. Used for the {@code glUniform1ui}-style "single
     * scalar uniform" pattern Voxy uses everywhere — caller supplies
     * the data via a direct {@code ByteBuffer}; backend emits the
     * appropriate per-stage call (Metal {@code setVertexBytes} +
     * {@code setFragmentBytes}, Vulkan {@code vkCmdPushConstants}, GL
     * UBO mirror).
     */
    void setBytes(int binding, long dataAddr, int dataSize);

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

    /**
     * Multi-draw indirect: read {@code drawCount} {@code VkDrawIndirectCommand}-shaped
     * structs from {@code buffer} starting at {@code offset}, separated by
     * {@code stride} bytes, and issue one draw per struct. Each struct holds
     * (vertexCount, instanceCount, firstVertex, firstInstance) as 4 uint32 values.
     *
     * On Metal this lowers to a CPU-side loop calling {@code drawPrimitives:indirectBuffer:};
     * Vulkan and GL use {@code vkCmdDrawIndirect} / {@code glMultiDrawArraysIndirect}.
     */
    void drawIndirect(int primitiveType, IGpuBuffer buffer, long offset,
                      int drawCount, int stride);

    /**
     * Indexed equivalent of {@link #drawIndirect}. Each struct in the indirect
     * buffer holds (indexCount, instanceCount, firstIndex, vertexOffset, firstInstance)
     * as 5 uint32 values. The index buffer must be bound via
     * {@link #bindIndexBuffer} first.
     */
    void drawIndexedIndirect(int primitiveType, IGpuBuffer buffer, long offset,
                              int drawCount, int stride);

    /**
     * Indexed multi-draw indirect with a GPU-resident draw count. Reads a
     * {@code uint32} from {@code countBuffer} at byte {@code countOffset}
     * giving the actual draw count {@code N}, then reads {@code N}
     * {@code VkDrawIndexedIndirectCommand}-shaped structs from
     * {@code drawBuffer} starting at {@code drawOffset}. {@code maxDrawCount}
     * is the upper bound enforced by the driver if the count buffer
     * yields a larger value.
     *
     * Used by {@code MDICSectionRenderer} so the GPU's compute-side
     * traversal decides how many sections to draw without a CPU round-trip.
     * On OpenGL this lowers to
     * {@code glMultiDrawElementsIndirectCountARB} (requires
     * {@code GL_ARB_indirect_parameters}). On Vulkan this lowers to
     * {@code vkCmdDrawIndexedIndirectCount} (core 1.2). On Metal this is
     * emulated via {@code MTLIndirectCommandBuffer} +
     * {@code executeCommandsInBuffer:indirectBuffer:indirectBufferOffset:}
     * (Blocker 1 — not yet implemented; the JNI lands alongside
     * MDICSectionRenderer's full migration).
     */
    void drawIndexedIndirectCount(int primitiveType,
                                  IGpuBuffer drawBuffer, long drawOffset,
                                  IGpuBuffer countBuffer, long countOffset,
                                  int maxDrawCount, int stride);

    /**
     * Execute the pre-encoded draws in an indirect command buffer. The
     * range of commands to execute is read from {@code rangeBuffer} at byte
     * {@code rangeOffset} — Metal expects a 16-byte {@code MTLIndirectCommandBufferExecutionRange}
     * ({@code uint32 location; uint32 length;} padded to 16). Other backends
     * use the equivalent shape.
     *
     * Used by MDIC's central render path on Metal once the cmdgen.comp
     * compute prepass writes the ICB rather than a plain
     * {@code DrawElementsIndirectCommand} array. On the OpenGL backend
     * this lowers to the existing
     * {@code glMultiDrawElementsIndirectCountARB} call against the ICB's
     * underlying buffer.
     */
    void executeCommandsInBuffer(IGpuIndirectCommandBuffer icb,
                                 IGpuBuffer rangeBuffer, long rangeOffset);

    @Override
    void close();
}
