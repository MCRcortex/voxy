package me.cortex.voxy.client.core.gpu;

/**
 * Central factory and utility interface for all GPU operations.
 * Each graphics API (OpenGL, Metal, Vulkan) provides its own implementation.
 *
 * This is the primary abstraction boundary — code that uses RenderBackend
 * can work with any graphics backend without modification.
 */
public interface RenderBackend {

    /**
     * Returns the type of this backend (e.g. OPENGL, METAL).
     */
    BackendType getType();

    // --- Resource Creation ---

    IGpuBuffer createBuffer(long size);
    IGpuBuffer createBuffer(long size, int flags);
    IGpuBuffer createBuffer(long size, int flags, boolean zero);

    IGpuTexture createTexture();
    IGpuTexture createTexture(int type);

    IGpuFramebuffer createFramebuffer();

    IGpuRenderBuffer createRenderBuffer(int format, int width, int height);

    IGpuVertexArray createVertexArray();

    IGpuFence createFence();

    IGpuPersistentBuffer createPersistentBuffer(long size, int flags);

    // --- Texture Operations ---

    void bindTextureUnit(int unit, int texture);
    void bindTextureUnit(int unit, int target, int texture);
    void textureParameteri(int texture, int pname, int param);
    void textureParameteri(int texture, int target, int pname, int param);
    void textureParameterf(int texture, int pname, float param);
    void textureParameterf(int texture, int target, int pname, float param);
    void textureSubImage2D(int texture, int target, int level, int x, int y, int w, int h, int format, int type, long addr);
    void textureStorage2D(int texture, int target, int levels, int format, int width, int height);

    // --- Framebuffer Operations ---

    void framebufferTexture(int fbo, int attachment, int texture, int level, int target);
    void framebufferRenderbuffer(int fbo, int attachment, int renderbuffer);
    void framebufferDrawBuffers(int fbo, int... buffers);
    int checkFramebufferStatus(int fbo);
    void clearDepthFramebuffer(int fbo, float depth);
    void clearDepthStencilFramebuffer(int fbo, float depth, int stencil);
    void blitFramebuffer(int readFbo, int drawFbo, int srcX0, int srcY0, int srcX1, int srcY1,
                         int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter);

    // --- Renderbuffer Operations ---

    int createRenderbufferId();
    void renderbufferStorage(int renderbuffer, int format, int width, int height);

    // --- Debug ---

    void objectLabel(int type, int id, String name);

    // --- Capabilities Query ---

    boolean hasCompute();
    boolean hasIndirectCount();
    boolean hasIndirectParameters();
    boolean hasSparseBuffer();
    long getMaxSSBOSize();

    // --- Static Vertex Array ---

    int getStaticVAO();

    // --- Resource statistics (for debug/F3 display) ---

    int getBufferCount();
    long getBufferTotalSize();
    int getTextureCount();
    long getTextureEstimatedTotalSize();

    // --- Cross-backend synchronization / copies ---

    /**
     * Inserts a memory barrier so subsequent operations observe prior writes.
     * `flags` uses GL memoryBarrier semantics (GL_BUFFER_UPDATE_BARRIER_BIT, etc.)
     * on OpenGL; Metal performs automatic hazard tracking between command
     * encoders so the Metal backend treats this as a no-op.
     */
    void memoryBarrier(int flags);

    /**
     * Copies `size` bytes from `src`+srcOffset to `dst`+dstOffset.
     * OpenGL uses glCopyNamedBufferSubData (or the bound-buffer fallback);
     * Metal enqueues a blit encoder on a transient command buffer.
     */
    void copyBufferSubData(IGpuBuffer src, IGpuBuffer dst, long srcOffset, long dstOffset, long size);

    /**
     * Persistent-buffer overload used by UploadStream.commit(): the upload
     * buffer is an IGpuPersistentBuffer but the destination is a regular
     * IGpuBuffer. Backends bridge the two without exposing raw handles to
     * the caller.
     */
    void copyBufferSubData(IGpuPersistentBuffer src, IGpuBuffer dst, long srcOffset, long dstOffset, long size);

    /**
     * Reverse persistent-buffer overload used by DownloadStream.commit():
     * a regular IGpuBuffer is the source, the persistent (CPU-mapped) buffer
     * is the destination. Lets readback paths flow through the backend without
     * raw GL — critical on Metal where the GL 4.1 context can't service
     * GL 4.5's glCopyNamedBufferSubData.
     */
    void copyBufferSubData(IGpuBuffer src, IGpuPersistentBuffer dst, long srcOffset, long dstOffset, long size);

    // --- Render pass encoding (M2 minimal surface; expanded in M5+) ---

    /**
     * Begin a render pass with the given description. The returned encoder
     * holds the in-flight state; call {@link RenderEncoder#close()} to end
     * encoding before issuing further work or beginning another pass.
     *
     * Backend semantics:
     *  - OpenGL: binds the framebuffer composed from the attachments,
     *    applies clear values via glClearColor/glClear, leaves the FBO
     *    bound until close() (which unbinds to default).
     *  - Metal: builds an MTLRenderPassDescriptor with load/store/clear
     *    actions and creates an MTLRenderCommandEncoder. Clear runs as
     *    part of the load action; close() ends encoding.
     *  - Vulkan: dynamic rendering — vkCmdBeginRendering with attachments;
     *    close() runs vkCmdEndRendering.
     */
    RenderEncoder beginRenderPass(RenderPassDesc desc);

    /**
     * Submit any pending command buffers to the GPU. On OpenGL this is a
     * glFlush (commands are already implicitly submitted); on Metal/Vulkan
     * this commits the active command buffer and rotates to a fresh one.
     */
    void submit();

    /**
     * Compile and link a graphics pipeline state object from the supplied
     * shader sources. Backends pick whichever representation they need —
     * Metal consumes MSL via mtlDeviceNewLibraryWithSource, Vulkan consumes
     * SPIRV via vkCreateShaderModule. The OpenGL backend is unsupported in
     * the M5 surface (Voxy's GL path keeps using its existing Shader.Builder
     * compile flow); it will be wired up in M9 when the migration covers
     * call sites that need the new abstraction on Win/Linux as well.
     */
    IGpuPipeline createGraphicsPipeline(GraphicsPipelineDesc desc);

    /**
     * Compile a compute pipeline state object. Same backend split as
     * {@link #createGraphicsPipeline} — Metal uses MSL, Vulkan SPIRV, GL
     * is deferred.
     */
    IGpuPipeline createComputePipeline(ComputePipelineDesc desc);

    /**
     * Build a sampler state object from the supplied description. Samplers
     * are immutable and shareable; one sampler can be bound to many shader
     * stages and many draws.
     */
    IGpuSampler createSampler(SamplerDesc desc);

    /**
     * Allocate an indirect command buffer holding up to {@code maxCommands}
     * draws. Used by MDIC-style render paths where the GPU itself decides
     * how many sections to draw — see {@link IGpuIndirectCommandBuffer}.
     *
     * Metal: {@code MTLDevice newIndirectCommandBufferWithDescriptor:maxCommandCount:options:}.
     * Vulkan: emulated via a buffer of {@code VkDrawIndexedIndirectCommand}
     * structs (vkCmdDrawIndexedIndirectCount consumes them natively).
     * OpenGL: thin wrapper holding the max-count metadata — the encoder's
     * GL-side draw path keeps using glMultiDrawElementsIndirectCountARB
     * against the caller's existing buffers.
     */
    IGpuIndirectCommandBuffer createIndirectCommandBuffer(int maxCommands);

    /**
     * Begin a compute pass. The returned encoder is the only handle for
     * issuing compute work until {@link ComputeEncoder#close()}.
     *
     * Backend semantics:
     *  - Metal: {@code commandBuffer newComputeCommandEncoder} on the active
     *    frame command buffer.
     *  - Vulkan: starts encoding compute commands on the active command
     *    buffer; barriers must be issued explicitly.
     *  - OpenGL: returns a thin shim over {@code glDispatchCompute};
     *    barriers fall through to {@link #memoryBarrier(int)}.
     */
    ComputeEncoder beginComputePass();
}
