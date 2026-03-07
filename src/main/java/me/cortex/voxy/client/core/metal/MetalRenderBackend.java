package me.cortex.voxy.client.core.metal;

import me.cortex.voxy.client.core.gpu.*;

/**
 * Metal rendering backend stub for macOS Apple Silicon (M-series) support.
 *
 * This backend will translate Voxy's rendering operations to Metal API calls
 * via the metal-java JNI bridge or a custom native library.
 *
 * Current status: STUB — all methods throw UnsupportedOperationException.
 * Implementation priority:
 *   1. Buffer management (MetalBuffer)
 *   2. Texture management (MetalTexture)
 *   3. Shader compilation (MSL from GLSL via SPIRV-Cross)
 *   4. Framebuffer/render pass setup
 *   5. Compute pipeline support
 *   6. Draw call encoding
 */
public class MetalRenderBackend implements RenderBackend {

    @Override
    public BackendType getType() {
        return BackendType.METAL;
    }

    // --- Resource Creation ---

    @Override
    public IGpuBuffer createBuffer(long size) {
        throw new UnsupportedOperationException("Metal backend not yet implemented");
    }

    @Override
    public IGpuBuffer createBuffer(long size, int flags) {
        throw new UnsupportedOperationException("Metal backend not yet implemented");
    }

    @Override
    public IGpuBuffer createBuffer(long size, int flags, boolean zero) {
        throw new UnsupportedOperationException("Metal backend not yet implemented");
    }

    @Override
    public IGpuTexture createTexture() {
        throw new UnsupportedOperationException("Metal backend not yet implemented");
    }

    @Override
    public IGpuTexture createTexture(int type) {
        throw new UnsupportedOperationException("Metal backend not yet implemented");
    }

    @Override
    public IGpuFramebuffer createFramebuffer() {
        throw new UnsupportedOperationException("Metal backend not yet implemented");
    }

    @Override
    public IGpuRenderBuffer createRenderBuffer(int format, int width, int height) {
        throw new UnsupportedOperationException("Metal backend not yet implemented");
    }

    @Override
    public IGpuVertexArray createVertexArray() {
        throw new UnsupportedOperationException("Metal backend not yet implemented");
    }

    @Override
    public IGpuFence createFence() {
        throw new UnsupportedOperationException("Metal backend not yet implemented");
    }

    @Override
    public IGpuPersistentBuffer createPersistentBuffer(long size, int flags) {
        throw new UnsupportedOperationException("Metal backend not yet implemented");
    }

    // --- Texture Operations ---

    @Override
    public void bindTextureUnit(int unit, int texture) {
        throw new UnsupportedOperationException("Metal backend not yet implemented");
    }

    @Override
    public void bindTextureUnit(int unit, int target, int texture) {
        throw new UnsupportedOperationException("Metal backend not yet implemented");
    }

    @Override
    public void textureParameteri(int texture, int pname, int param) {
        throw new UnsupportedOperationException("Metal backend not yet implemented");
    }

    @Override
    public void textureParameteri(int texture, int target, int pname, int param) {
        throw new UnsupportedOperationException("Metal backend not yet implemented");
    }

    @Override
    public void textureParameterf(int texture, int pname, float param) {
        throw new UnsupportedOperationException("Metal backend not yet implemented");
    }

    @Override
    public void textureParameterf(int texture, int target, int pname, float param) {
        throw new UnsupportedOperationException("Metal backend not yet implemented");
    }

    @Override
    public void textureSubImage2D(int texture, int target, int level, int x, int y, int w, int h, int format, int type, long addr) {
        throw new UnsupportedOperationException("Metal backend not yet implemented");
    }

    @Override
    public void textureStorage2D(int texture, int target, int levels, int format, int width, int height) {
        throw new UnsupportedOperationException("Metal backend not yet implemented");
    }

    // --- Framebuffer Operations ---

    @Override
    public void framebufferTexture(int fbo, int attachment, int texture, int level, int target) {
        throw new UnsupportedOperationException("Metal backend not yet implemented");
    }

    @Override
    public void framebufferRenderbuffer(int fbo, int attachment, int renderbuffer) {
        throw new UnsupportedOperationException("Metal backend not yet implemented");
    }

    @Override
    public void framebufferDrawBuffers(int fbo, int... buffers) {
        throw new UnsupportedOperationException("Metal backend not yet implemented");
    }

    @Override
    public int checkFramebufferStatus(int fbo) {
        throw new UnsupportedOperationException("Metal backend not yet implemented");
    }

    @Override
    public void clearDepthFramebuffer(int fbo, float depth) {
        throw new UnsupportedOperationException("Metal backend not yet implemented");
    }

    @Override
    public void clearDepthStencilFramebuffer(int fbo, float depth, int stencil) {
        throw new UnsupportedOperationException("Metal backend not yet implemented");
    }

    @Override
    public void blitFramebuffer(int readFbo, int drawFbo, int srcX0, int srcY0, int srcX1, int srcY1,
                                int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        throw new UnsupportedOperationException("Metal backend not yet implemented");
    }

    // --- Renderbuffer Operations ---

    @Override
    public int createRenderbufferId() {
        throw new UnsupportedOperationException("Metal backend not yet implemented");
    }

    @Override
    public void renderbufferStorage(int renderbuffer, int format, int width, int height) {
        throw new UnsupportedOperationException("Metal backend not yet implemented");
    }

    // --- Debug ---

    @Override
    public void objectLabel(int type, int id, String name) {
        // Metal has its own debug labeling; no-op for now
    }

    // --- Capabilities ---

    @Override
    public boolean hasCompute() {
        return true; // Metal always supports compute
    }

    @Override
    public boolean hasIndirectCount() {
        return true; // Metal supports indirect command buffers
    }

    @Override
    public boolean hasIndirectParameters() {
        return true;
    }

    @Override
    public boolean hasSparseBuffer() {
        return false; // Metal has sparse textures but not sparse buffers in the same way
    }

    @Override
    public long getMaxSSBOSize() {
        // Metal buffer size limit is typically device-dependent, commonly 256MB+
        return 256L * 1024 * 1024;
    }

    @Override
    public int getStaticVAO() {
        throw new UnsupportedOperationException("Metal does not use VAOs");
    }
}
