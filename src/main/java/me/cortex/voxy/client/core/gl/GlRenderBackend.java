package me.cortex.voxy.client.core.gl;

import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gpu.*;

/**
 * OpenGL implementation of the RenderBackend interface.
 * Delegates to the existing GL wrapper classes (GlBuffer, GlTexture, etc.)
 * and the GLCompat compatibility layer.
 */
public class GlRenderBackend implements RenderBackend {

    @Override
    public BackendType getType() {
        return BackendType.OPENGL;
    }

    // --- Resource Creation ---

    @Override
    public IGpuBuffer createBuffer(long size) {
        return new GlBuffer(size);
    }

    @Override
    public IGpuBuffer createBuffer(long size, int flags) {
        return new GlBuffer(size, flags);
    }

    @Override
    public IGpuBuffer createBuffer(long size, int flags, boolean zero) {
        return new GlBuffer(size, flags, zero);
    }

    @Override
    public IGpuTexture createTexture() {
        return new GlTexture();
    }

    @Override
    public IGpuTexture createTexture(int type) {
        return new GlTexture(type);
    }

    @Override
    public IGpuFramebuffer createFramebuffer() {
        return new GlFramebuffer();
    }

    @Override
    public IGpuRenderBuffer createRenderBuffer(int format, int width, int height) {
        return new GlRenderBuffer(format, width, height);
    }

    @Override
    public IGpuVertexArray createVertexArray() {
        return new GlVertexArray();
    }

    @Override
    public IGpuFence createFence() {
        return new GlFence();
    }

    @Override
    public IGpuPersistentBuffer createPersistentBuffer(long size, int flags) {
        return new GlPersistentMappedBuffer(size, flags);
    }

    // --- Texture Operations (delegate to GLCompat) ---

    @Override
    public void bindTextureUnit(int unit, int texture) {
        GLCompat.bindTextureUnit(unit, texture);
    }

    @Override
    public void bindTextureUnit(int unit, int target, int texture) {
        GLCompat.bindTextureUnit(unit, target, texture);
    }

    @Override
    public void textureParameteri(int texture, int pname, int param) {
        GLCompat.textureParameteri(texture, pname, param);
    }

    @Override
    public void textureParameteri(int texture, int target, int pname, int param) {
        GLCompat.textureParameteri(texture, target, pname, param);
    }

    @Override
    public void textureParameterf(int texture, int pname, float param) {
        GLCompat.textureParameterf(texture, pname, param);
    }

    @Override
    public void textureParameterf(int texture, int target, int pname, float param) {
        GLCompat.textureParameterf(texture, target, pname, param);
    }

    @Override
    public void textureSubImage2D(int texture, int target, int level, int x, int y, int w, int h, int format, int type, long addr) {
        GLCompat.textureSubImage2D(texture, target, level, x, y, w, h, format, type, addr);
    }

    @Override
    public void textureStorage2D(int texture, int target, int levels, int format, int width, int height) {
        GLCompat.textureStorage2D(texture, target, levels, format, width, height);
    }

    // --- Framebuffer Operations ---

    @Override
    public void framebufferTexture(int fbo, int attachment, int texture, int level, int target) {
        GLCompat.framebufferTexture(fbo, attachment, texture, level, target);
    }

    @Override
    public void framebufferRenderbuffer(int fbo, int attachment, int renderbuffer) {
        GLCompat.framebufferRenderbuffer(fbo, attachment, renderbuffer);
    }

    @Override
    public void framebufferDrawBuffers(int fbo, int... buffers) {
        GLCompat.framebufferDrawBuffers(fbo, buffers);
    }

    @Override
    public int checkFramebufferStatus(int fbo) {
        return GLCompat.checkFramebufferStatus(fbo);
    }

    @Override
    public void clearDepthFramebuffer(int fbo, float depth) {
        GLCompat.clearDepthFramebuffer(fbo, depth);
    }

    @Override
    public void clearDepthStencilFramebuffer(int fbo, float depth, int stencil) {
        GLCompat.clearDepthStencilFramebuffer(fbo, depth, stencil);
    }

    @Override
    public void blitFramebuffer(int readFbo, int drawFbo, int srcX0, int srcY0, int srcX1, int srcY1,
                                int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        GLCompat.blitFramebuffer(readFbo, drawFbo, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    // --- Renderbuffer Operations ---

    @Override
    public int createRenderbufferId() {
        return GLCompat.createRenderbuffer();
    }

    @Override
    public void renderbufferStorage(int renderbuffer, int format, int width, int height) {
        GLCompat.renderbufferStorage(renderbuffer, format, width, height);
    }

    // --- Debug ---

    @Override
    public void objectLabel(int type, int id, String name) {
        if (GlDebug.GL_DEBUG) {
            org.lwjgl.opengl.GL43C.glObjectLabel(type, id, name);
        }
    }

    // --- Capabilities ---

    @Override
    public boolean hasCompute() {
        return Capabilities.INSTANCE.compute;
    }

    @Override
    public boolean hasIndirectCount() {
        return Capabilities.INSTANCE.indirectCount;
    }

    @Override
    public boolean hasIndirectParameters() {
        return Capabilities.INSTANCE.indirectParameters;
    }

    @Override
    public boolean hasSparseBuffer() {
        return Capabilities.INSTANCE.sparseBuffer;
    }

    @Override
    public long getMaxSSBOSize() {
        return Capabilities.INSTANCE.ssboMaxSize;
    }

    @Override
    public int getStaticVAO() {
        return GlVertexArray.STATIC_VAO;
    }

    @Override
    public int getBufferCount() {
        return GlBuffer.getCount();
    }

    @Override
    public long getBufferTotalSize() {
        return GlBuffer.getTotalSize();
    }

    @Override
    public int getTextureCount() {
        return GlTexture.getCount();
    }

    @Override
    public long getTextureEstimatedTotalSize() {
        return GlTexture.getEstimatedTotalSize();
    }

    @Override
    public void memoryBarrier(int flags) {
        org.lwjgl.opengl.GL42.glMemoryBarrier(flags);
    }

    @Override
    public void copyBufferSubData(IGpuBuffer src, IGpuBuffer dst, long srcOffset, long dstOffset, long size) {
        copyBufferSubDataById(src.id(), dst.id(), srcOffset, dstOffset, size);
    }

    @Override
    public void copyBufferSubData(IGpuPersistentBuffer src, IGpuBuffer dst, long srcOffset, long dstOffset, long size) {
        copyBufferSubDataById(src.id(), dst.id(), srcOffset, dstOffset, size);
    }

    private static final int GL_COPY_READ_BUFFER_BINDING = 0x8F36;
    private static final int GL_COPY_WRITE_BUFFER_BINDING = 0x8F37;

    private static void copyBufferSubDataById(int srcId, int dstId, long srcOffset, long dstOffset, long size) {
        if (size <= 0) return;
        boolean hasDSA = org.lwjgl.opengl.GL.getCapabilities().GL_ARB_direct_state_access
                || org.lwjgl.opengl.GL.getCapabilities().OpenGL45;
        if (hasDSA) {
            org.lwjgl.opengl.GL45C.glCopyNamedBufferSubData(srcId, dstId, srcOffset, dstOffset, size);
        } else {
            int prevRead = org.lwjgl.opengl.GL15C.glGetInteger(GL_COPY_READ_BUFFER_BINDING);
            int prevWrite = org.lwjgl.opengl.GL15C.glGetInteger(GL_COPY_WRITE_BUFFER_BINDING);
            org.lwjgl.opengl.GL15C.glBindBuffer(org.lwjgl.opengl.GL31C.GL_COPY_READ_BUFFER, srcId);
            org.lwjgl.opengl.GL15C.glBindBuffer(org.lwjgl.opengl.GL31C.GL_COPY_WRITE_BUFFER, dstId);
            org.lwjgl.opengl.GL31C.glCopyBufferSubData(
                    org.lwjgl.opengl.GL31C.GL_COPY_READ_BUFFER,
                    org.lwjgl.opengl.GL31C.GL_COPY_WRITE_BUFFER,
                    srcOffset, dstOffset, size);
            org.lwjgl.opengl.GL15C.glBindBuffer(org.lwjgl.opengl.GL31C.GL_COPY_READ_BUFFER, prevRead);
            org.lwjgl.opengl.GL15C.glBindBuffer(org.lwjgl.opengl.GL31C.GL_COPY_WRITE_BUFFER, prevWrite);
        }
    }

    // --- Render pass encoding ---

    @Override
    public RenderEncoder beginRenderPass(RenderPassDesc desc) {
        // Allocate a transient framebuffer for the pass and attach the color/depth
        // textures. We don't cache because Voxy's M2 caller (just clears) is
        // low-frequency; M5+ will introduce a per-attachment-set FBO cache when
        // pipeline state binding lands.
        int fbo = org.lwjgl.opengl.GL45C.glCreateFramebuffers();
        int[] drawBuffers = new int[Math.max(1, desc.colorAttachments().size())];
        int clearMask = 0;
        for (int i = 0; i < desc.colorAttachments().size(); i++) {
            RenderPassDesc.ColorAttachment c = desc.colorAttachments().get(i);
            int attachment = org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0 + i;
            org.lwjgl.opengl.GL45C.glNamedFramebufferTexture(fbo, attachment, c.texture().id(), c.level());
            drawBuffers[i] = attachment;
            if (c.loadAction() == RenderPassDesc.LoadAction.CLEAR) {
                org.lwjgl.opengl.GL45C.glClearNamedFramebufferfv(fbo, org.lwjgl.opengl.GL30C.GL_COLOR, i,
                        new float[]{c.clearR(), c.clearG(), c.clearB(), c.clearA()});
            }
        }
        if (!desc.colorAttachments().isEmpty()) {
            org.lwjgl.opengl.GL45C.glNamedFramebufferDrawBuffers(fbo, drawBuffers);
        }
        if (desc.depthAttachment() != null) {
            RenderPassDesc.DepthAttachment d = desc.depthAttachment();
            org.lwjgl.opengl.GL45C.glNamedFramebufferTexture(fbo,
                    org.lwjgl.opengl.GL30C.GL_DEPTH_ATTACHMENT, d.texture().id(), d.level());
            if (d.loadAction() == RenderPassDesc.LoadAction.CLEAR) {
                org.lwjgl.opengl.GL45C.glClearNamedFramebufferfv(fbo,
                        org.lwjgl.opengl.GL30C.GL_DEPTH, 0, new float[]{d.clearDepth()});
            }
        }
        org.lwjgl.opengl.GL45C.glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER, fbo);
        org.lwjgl.opengl.GL11C.glViewport(0, 0, desc.viewportWidth(), desc.viewportHeight());

        return () -> {
            org.lwjgl.opengl.GL45C.glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER, 0);
            org.lwjgl.opengl.GL45C.glDeleteFramebuffers(fbo);
        };
    }

    @Override
    public void submit() {
        org.lwjgl.opengl.GL11C.glFlush();
    }
}
