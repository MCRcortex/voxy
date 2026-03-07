package me.cortex.voxy.client.core.metal;

import me.cortex.voxy.client.core.gpu.IGpuFramebuffer;
import me.cortex.voxy.client.core.gpu.IGpuRenderBuffer;
import me.cortex.voxy.client.core.gpu.IGpuTexture;
import me.cortex.voxy.common.util.TrackedObject;

/**
 * Metal implementation of IGpuFramebuffer.
 *
 * Metal doesn't have a direct equivalent to OpenGL's FBO. Instead, render targets
 * are specified per-pass via MTLRenderPassDescriptor. This class maintains the
 * attachment configuration and lazily creates a render pass descriptor when needed.
 *
 * Attachment state is tracked on the Java side and applied to a fresh
 * MTLRenderPassDescriptor for each render pass.
 */
public class MetalFramebuffer extends TrackedObject implements IGpuFramebuffer {
    private final int id;
    private String debugName;

    // Track attachments (up to 8 color + depth + stencil)
    private static final int MAX_COLOR_ATTACHMENTS = 8;
    private final long[] colorTextures = new long[MAX_COLOR_ATTACHMENTS];
    private final int[] colorLevels = new int[MAX_COLOR_ATTACHMENTS];
    private long depthTexture;
    private int depthLevel;
    private long stencilTexture;
    private int stencilLevel;
    private int[] drawBuffers;

    private static int NEXT_ID_COUNTER = 1;

    public MetalFramebuffer() {
        this.id = NEXT_ID_COUNTER++;
        MetalHandleMap.register(this.id); // register with a sentinel handle
    }

    @Override
    public int id() {
        return this.id;
    }

    @Override
    public IGpuFramebuffer bind(int attachment, IGpuTexture texture) {
        return this.bind(attachment, texture, 0);
    }

    @Override
    public IGpuFramebuffer bind(int attachment, IGpuTexture texture, int level) {
        long texHandle = ((MetalTexture) texture).getHandle();
        if (MetalFormatUtil.isColorAttachment(attachment)) {
            int index = MetalFormatUtil.colorAttachmentIndex(attachment);
            this.colorTextures[index] = texHandle;
            this.colorLevels[index] = level;
        }
        if (MetalFormatUtil.isDepthAttachment(attachment)) {
            this.depthTexture = texHandle;
            this.depthLevel = level;
        }
        if (MetalFormatUtil.isStencilAttachment(attachment)) {
            this.stencilTexture = texHandle;
            this.stencilLevel = level;
        }
        return this;
    }

    @Override
    public IGpuFramebuffer bind(int attachment, IGpuRenderBuffer buffer) {
        // Metal renderbuffers are just textures with render target usage
        long texHandle = ((MetalRenderBuffer) buffer).getTextureHandle();
        if (MetalFormatUtil.isColorAttachment(attachment)) {
            int index = MetalFormatUtil.colorAttachmentIndex(attachment);
            this.colorTextures[index] = texHandle;
            this.colorLevels[index] = 0;
        }
        if (MetalFormatUtil.isDepthAttachment(attachment)) {
            this.depthTexture = texHandle;
            this.depthLevel = 0;
        }
        if (MetalFormatUtil.isStencilAttachment(attachment)) {
            this.stencilTexture = texHandle;
            this.stencilLevel = 0;
        }
        return this;
    }

    @Override
    public IGpuFramebuffer setDrawBuffers(int... buffers) {
        this.drawBuffers = buffers.clone();
        return this;
    }

    @Override
    public IGpuFramebuffer verify() {
        // Metal validates render pass descriptors at encoder creation time.
        // We do a basic sanity check here.
        boolean hasAnyAttachment = (this.depthTexture != 0) || (this.stencilTexture != 0);
        for (long colorTex : this.colorTextures) {
            if (colorTex != 0) {
                hasAnyAttachment = true;
                break;
            }
        }
        if (!hasAnyAttachment) {
            throw new IllegalStateException("Metal framebuffer has no attachments");
        }
        return this;
    }

    @Override
    public IGpuFramebuffer name(String name) {
        this.debugName = name;
        return this;
    }

    /**
     * Builds a MTLRenderPassDescriptor from the current attachment state.
     * @param clearDepth depth clear value, or NaN to load
     * @param clearStencil stencil clear value, or -1 to load
     * @return native render pass descriptor handle (caller must release)
     */
    public long buildRenderPassDescriptor(float clearDepth, int clearStencil) {
        long rpd = MetalNative.mtlNewRenderPassDescriptor();

        for (int i = 0; i < MAX_COLOR_ATTACHMENTS; i++) {
            if (this.colorTextures[i] != 0) {
                MetalNative.mtlRenderPassSetColorAttachment(rpd, i,
                        this.colorTextures[i],
                        MetalNative.MTLLoadActionLoad,
                        MetalNative.MTLStoreActionStore,
                        this.colorLevels[i]);
            }
        }

        if (this.depthTexture != 0) {
            int loadAction = Float.isNaN(clearDepth)
                    ? MetalNative.MTLLoadActionLoad
                    : MetalNative.MTLLoadActionClear;
            MetalNative.mtlRenderPassSetDepthAttachment(rpd,
                    this.depthTexture, loadAction, MetalNative.MTLStoreActionStore,
                    Float.isNaN(clearDepth) ? 1.0f : clearDepth,
                    this.depthLevel);
        }

        if (this.stencilTexture != 0) {
            int loadAction = clearStencil < 0
                    ? MetalNative.MTLLoadActionLoad
                    : MetalNative.MTLLoadActionClear;
            MetalNative.mtlRenderPassSetStencilAttachment(rpd,
                    this.stencilTexture, loadAction, MetalNative.MTLStoreActionStore,
                    clearStencil < 0 ? 0 : clearStencil,
                    this.stencilLevel);
        }

        return rpd;
    }

    /**
     * Builds a render pass descriptor with default load actions (no clearing).
     */
    public long buildRenderPassDescriptor() {
        return buildRenderPassDescriptor(Float.NaN, -1);
    }

    @Override
    public void free() {
        super.free0();
        MetalHandleMap.unregister(this.id);
    }

    public String getDebugName() {
        return this.debugName;
    }
}
