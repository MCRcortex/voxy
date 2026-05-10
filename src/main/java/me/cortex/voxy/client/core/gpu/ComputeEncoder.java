package me.cortex.voxy.client.core.gpu;

/**
 * In-flight encoder for a compute pass.
 *
 * Created by {@link RenderBackend#beginComputePass()} and closed via
 * {@link #close()} (which ends encoding on Metal/Vulkan).
 *
 * M7 surface covers what Voxy's compute pipeline needs at minimum:
 *   - Pipeline binding
 *   - SSBO/UBO buffer binding (Voxy's main mechanism for compute data layout)
 *   - Group-count dispatch
 *
 * Texture binding and indirect dispatch land in M8/M9 alongside the
 * {@code HiZBuffer2} and traversal migrations.
 */
public interface ComputeEncoder extends AutoCloseable {

    // --- Pipeline barrier scope flags (mirror Vulkan VK_PIPELINE_STAGE_* but
    // collapsed to the categories Voxy actually uses). The Metal backend
    // typically auto-tracks hazards and treats barrier() as a hint.

    /** Stage covers shader storage buffer/image read+write. */
    int BARRIER_SHADER = 0x1;
    /** Stage covers indirect command + draw/dispatch arg fetches. */
    int BARRIER_INDIRECT = 0x2;
    /** Stage covers buffer-to-buffer / buffer-to-texture transfer (blit). */
    int BARRIER_TRANSFER = 0x4;

    /** Bind a compute pipeline state object. All subsequent dispatches use it. */
    void setPipeline(IGpuPipeline pipeline);

    /**
     * Bind a buffer at the given binding index in the active shader's binding
     * set 0. Voxy's GLSL uses {@code layout(binding = N) buffer ...}; we map
     * those bindings 1:1 across backends.
     */
    void setBuffer(int binding, IGpuBuffer buffer, long offset);

    /**
     * Bind a texture as a storage image at the given binding index. Used by
     * Voxy's HiZ pass and similar compute writers. Metal binds via
     * {@code setTexture:atIndex:}; Vulkan emits a descriptor write of type
     * STORAGE_IMAGE.
     */
    void setTexture(int binding, IGpuTexture texture);

    /**
     * Dispatch (groupCountX × groupCountY × groupCountZ) thread-groups. The
     * thread-group size itself comes from the bound pipeline (declared as
     * {@code layout(local_size_x=...)} in the shader).
     */
    void dispatch(int groupCountX, int groupCountY, int groupCountZ);

    /**
     * Indirect dispatch — read three uint32 values (groupCountX, Y, Z) from
     * {@code buffer} at byte {@code offset} and use them as the dispatch
     * dimensions. Voxy's hierarchical traversal uses this to keep the
     * dispatch count entirely on the GPU.
     */
    void dispatchIndirect(IGpuBuffer buffer, long offset);

    /**
     * Insert a memory + execution barrier between previous compute work
     * (output of stages identified by {@code srcStages}) and subsequent
     * dispatches that read it (input stages identified by {@code dstStages}).
     * Bit values are the {@code BARRIER_*} constants on this interface.
     *
     * On Metal this is mostly a hint — the driver auto-tracks hazards
     * between encoders, but writes within the same encoder pass need an
     * explicit {@code memoryBarrierWithScope:}. On Vulkan it lowers to
     * {@code vkCmdPipelineBarrier} with the corresponding stage/access
     * masks.
     */
    void barrier(int srcStages, int dstStages);

    @Override
    void close();
}
