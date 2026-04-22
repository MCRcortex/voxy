package me.cortex.voxy.client.core.metal;

import me.cortex.voxy.client.core.gpu.*;
import me.cortex.voxy.common.Logger;

import java.util.concurrent.atomic.AtomicLong;

import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;

/**
 * Metal rendering backend for macOS Apple Silicon (M-series) support.
 *
 * This backend translates Voxy's rendering operations to Metal API calls
 * via the MetalNative JNI bridge. It manages the Metal device, command queue,
 * and shared event for fence synchronization.
 *
 * Metal architectural differences from OpenGL:
 *   - No global state machine; state is captured in pipeline state objects
 *   - No framebuffer objects; render targets are per-pass via render pass descriptors
 *   - No VAOs; vertex layout is part of the render pipeline state
 *   - Buffers in shared storage are always CPU-visible (unified memory)
 *   - Texture state (filtering, wrapping) is set via sampler objects, not on textures
 *   - Shaders are compiled to MTLLibrary from MSL (not GLSL)
 */
public class MetalRenderBackend implements RenderBackend {

    private final long device;
    private final long commandQueue;
    private final long sharedEvent;
    private final AtomicLong fenceCounter = new AtomicLong(1);
    private final long maxBufferLength;

    public MetalRenderBackend() {
        if (!MetalNative.load()) {
            throw new RuntimeException("Metal native library is not available");
        }

        this.device = MetalNative.mtlCreateSystemDefaultDevice();
        if (this.device == 0) {
            throw new RuntimeException("Failed to create Metal device");
        }

        String deviceName = MetalNative.mtlDeviceGetName(this.device);
        Logger.info("Metal device: " + deviceName);

        this.commandQueue = MetalNative.mtlDeviceNewCommandQueue(this.device);
        if (this.commandQueue == 0) {
            throw new RuntimeException("Failed to create Metal command queue");
        }

        this.sharedEvent = MetalNative.mtlDeviceNewSharedEvent(this.device);
        if (this.sharedEvent == 0) {
            throw new RuntimeException("Failed to create Metal shared event");
        }

        this.maxBufferLength = MetalNative.mtlDeviceMaxBufferLength(this.device);
    }

    @Override
    public BackendType getType() {
        return BackendType.METAL;
    }

    // --- Resource Creation ---

    @Override
    public IGpuBuffer createBuffer(long size) {
        return createBuffer(size, 0, true);
    }

    @Override
    public IGpuBuffer createBuffer(long size, int flags) {
        return createBuffer(size, flags, true);
    }

    @Override
    public IGpuBuffer createBuffer(long size, int flags, boolean zero) {
        return new MetalBuffer(this.device, size,
                MetalNative.MTLResourceStorageModeShared, zero);
    }

    @Override
    public IGpuTexture createTexture() {
        return new MetalTexture(this.device, GL_TEXTURE_2D);
    }

    @Override
    public IGpuTexture createTexture(int type) {
        return new MetalTexture(this.device, type);
    }

    @Override
    public IGpuFramebuffer createFramebuffer() {
        return new MetalFramebuffer();
    }

    @Override
    public IGpuRenderBuffer createRenderBuffer(int format, int width, int height) {
        return new MetalRenderBuffer(this.device, format, width, height);
    }

    @Override
    public IGpuVertexArray createVertexArray() {
        return new MetalVertexDescriptor();
    }

    @Override
    public IGpuFence createFence() {
        long targetValue = this.fenceCounter.getAndIncrement();

        long cmdBuffer = MetalNative.mtlCommandQueueNewCommandBuffer(this.commandQueue);
        MetalNative.mtlCommandBufferEncodeSignalEvent(cmdBuffer, this.sharedEvent, targetValue);
        MetalNative.mtlCommandBufferCommit(cmdBuffer);
        MetalNative.mtlRelease(cmdBuffer);

        return new MetalFence(this.sharedEvent, targetValue);
    }

    @Override
    public IGpuPersistentBuffer createPersistentBuffer(long size, int flags) {
        return new MetalPersistentBuffer(this.device, size, flags);
    }

    // --- Texture Operations ---

    @Override
    public void bindTextureUnit(int unit, int texture) {
        // Metal binds textures per-encoder, not globally. Tracked for draw-time binding.
    }

    @Override
    public void bindTextureUnit(int unit, int target, int texture) {
        // Same — no global texture unit state in Metal
    }

    @Override
    public void textureParameteri(int texture, int pname, int param) {
        // Metal sets filtering/wrapping via MTLSamplerState, not on the texture
    }

    @Override
    public void textureParameteri(int texture, int target, int pname, int param) {
        // Same
    }

    @Override
    public void textureParameterf(int texture, int pname, float param) {
        // Sampler state
    }

    @Override
    public void textureParameterf(int texture, int target, int pname, float param) {
        // Same
    }

    @Override
    public void textureSubImage2D(int texture, int target, int level, int x, int y,
                                   int w, int h, int format, int type, long addr) {
        long texHandle = MetalHandleMap.getHandle(texture);
        int bpp = (int) MetalFormatUtil.bytesPerPixel(format);
        MetalNative.mtlTextureReplaceRegion(texHandle, level, x, y, w, h, addr, w * bpp);
    }

    @Override
    public void textureStorage2D(int texture, int target, int levels, int format, int width, int height) {
        // Metal allocates storage at texture creation time (MetalTexture.store())
    }

    // --- Framebuffer Operations ---

    @Override
    public void framebufferTexture(int fbo, int attachment, int texture, int level, int target) {
        // Handled by MetalFramebuffer.bind() when using the object API
    }

    @Override
    public void framebufferRenderbuffer(int fbo, int attachment, int renderbuffer) {
        // Handled by MetalFramebuffer.bind()
    }

    @Override
    public void framebufferDrawBuffers(int fbo, int... buffers) {
        // Metal configures this via render pass descriptor color attachments
    }

    @Override
    public int checkFramebufferStatus(int fbo) {
        return 0x8CD5; // GL_FRAMEBUFFER_COMPLETE — Metal validates at encoder creation
    }

    @Override
    public void clearDepthFramebuffer(int fbo, float depth) {
        // Deferred to render pass descriptor load action
    }

    @Override
    public void clearDepthStencilFramebuffer(int fbo, float depth, int stencil) {
        // Deferred to render pass descriptor load action
    }

    @Override
    public void blitFramebuffer(int readFbo, int drawFbo, int srcX0, int srcY0, int srcX1, int srcY1,
                                 int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        // TODO: Implement via blit command encoder or fullscreen render pass
    }

    // --- Renderbuffer Operations ---

    @Override
    public int createRenderbufferId() {
        return MetalHandleMap.register(1); // Placeholder — actual texture created in createRenderBuffer
    }

    @Override
    public void renderbufferStorage(int renderbuffer, int format, int width, int height) {
        // Metal renderbuffers are textures — storage is allocated at creation time
    }

    // --- Debug ---

    @Override
    public void objectLabel(int type, int id, String name) {
        try {
            long handle = MetalHandleMap.getHandle(id);
            MetalNative.mtlSetLabel(handle, name);
        } catch (IllegalArgumentException e) {
            // Unknown ID — silently ignore for debug labeling
        }
    }

    // --- Capabilities ---

    @Override
    public boolean hasCompute() {
        return true;
    }

    @Override
    public boolean hasIndirectCount() {
        return true;
    }

    @Override
    public boolean hasIndirectParameters() {
        return true;
    }

    @Override
    public boolean hasSparseBuffer() {
        return false;
    }

    @Override
    public long getMaxSSBOSize() {
        return this.maxBufferLength;
    }

    @Override
    public int getStaticVAO() {
        return 0; // Metal doesn't use VAOs
    }

    // --- Resource statistics ---

    @Override
    public int getBufferCount() {
        return MetalBuffer.getCount();
    }

    @Override
    public long getBufferTotalSize() {
        return MetalBuffer.getTotalSize();
    }

    @Override
    public int getTextureCount() {
        return MetalTexture.getCount();
    }

    @Override
    public long getTextureEstimatedTotalSize() {
        return MetalTexture.getEstimatedTotalSize();
    }

    @Override
    public void memoryBarrier(int flags) {
        // Metal performs automatic hazard tracking between encoders by default,
        // so most glMemoryBarrier bits are implicit. Explicit fences are needed
        // only for untracked resources, which we don't currently create.
    }

    @Override
    public void copyBufferSubData(IGpuBuffer src, IGpuBuffer dst, long srcOffset, long dstOffset, long size) {
        if (size <= 0) return;
        if (!(src instanceof MetalBuffer) || !(dst instanceof MetalBuffer)) {
            throw new IllegalArgumentException("copyBufferSubData on Metal backend requires MetalBuffer arguments");
        }
        long srcHandle = ((MetalBuffer) src).getHandle();
        long dstHandle = ((MetalBuffer) dst).getHandle();

        long cmdBuf = MetalNative.mtlCommandQueueNewCommandBuffer(this.commandQueue);
        long blit = MetalNative.mtlCommandBufferNewBlitEncoder(cmdBuf);
        MetalNative.mtlBlitEncoderCopyBuffer(blit, srcHandle, srcOffset, dstHandle, dstOffset, size);
        MetalNative.mtlEncoderEndEncoding(blit);
        MetalNative.mtlRelease(blit);
        MetalNative.mtlCommandBufferCommit(cmdBuf);
        MetalNative.mtlRelease(cmdBuf);
    }

    // --- Metal-specific accessors ---

    public long getDevice() {
        return this.device;
    }

    public long getCommandQueue() {
        return this.commandQueue;
    }

    public long getSharedEvent() {
        return this.sharedEvent;
    }

    public long newCommandBuffer() {
        return MetalNative.mtlCommandQueueNewCommandBuffer(this.commandQueue);
    }

    public void shutdown() {
        MetalNative.mtlRelease(this.sharedEvent);
        MetalNative.mtlRelease(this.commandQueue);
        MetalNative.mtlRelease(this.device);
        Logger.info("Metal backend shut down");
    }
}
