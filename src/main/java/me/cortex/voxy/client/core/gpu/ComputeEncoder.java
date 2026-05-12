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
     * Bind a sub-range of a persistent (CPU-mapped) buffer at the given
     * binding index. Used by {@code UploadStream}-fed compute paths
     * (AsyncNodeManager.tick, NodeCleaner.updateIds) where the data was just
     * memcpy'd into the persistent buffer and needs to be visible to the
     * compute shader. On OpenGL lowers to
     * {@code glBindBufferRange(GL_SHADER_STORAGE_BUFFER, ...)}; on Metal
     * binds the underlying MTLBuffer at the given offset (Metal doesn't carry
     * an explicit "size" — the shader determines the read range from its
     * binding type).
     */
    void setBuffer(int binding, IGpuPersistentBuffer buffer, long offset, long size);

    /**
     * Bind a <b>sampled</b> texture at the given binding index. The shader
     * accesses it via {@code sampler2D} / {@code sampler3D} / {@code samplerCube}
     * (paired with {@link #setSampler}). For shader-writable
     * {@code image2D}/{@code image3D} bindings, use {@link #setStorageImage}.
     *
     * Metal binds via {@code setTexture:atIndex:} (unified texture slot).
     * Vulkan emits a SAMPLED_IMAGE descriptor write. OpenGL binds to the
     * texture unit at index {@code binding}, so the paired sampler from
     * {@link #setSampler} lands on the same unit.
     */
    void setTexture(int binding, IGpuTexture texture);

    /**
     * Bind a single texture mip level as a shader-writable storage image at
     * the given binding index. Shader accesses it via
     * {@code image2D}/{@code image3D}. Format is read from the texture's
     * stored internal format.
     *
     * On OpenGL this lowers to
     * {@code glBindImageTexture(binding, tex.id, level, layered, 0,
     * GL_READ_WRITE, format)}. Metal currently ignores {@code level} —
     * per-mip texture views land alongside the HiZBuffer2 migration
     * (M9 Phase 2 follow-up). Vulkan writes a per-mip
     * {@code VkImageView} via a STORAGE_IMAGE descriptor.
     */
    void setStorageImage(int binding, IGpuTexture texture, int level);

    /**
     * Bind a sampler state object at the given binding index. Pairs with
     * {@link #setTexture} for sampled-texture compute passes (e.g. HiZ
     * mip generation that samples the previous mip).
     */
    void setSampler(int binding, IGpuSampler sampler);

    /**
     * Push a small block of bytes inline as a uniform buffer at the given
     * binding index. Same semantics as {@link RenderEncoder#setBytes}.
     */
    void setBytes(int binding, long dataAddr, int dataSize);

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
