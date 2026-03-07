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
}
