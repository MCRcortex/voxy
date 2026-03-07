package me.cortex.voxy.client.core.metal;

import me.cortex.voxy.client.core.gpu.IGpuRenderBuffer;
import me.cortex.voxy.common.util.TrackedObject;

/**
 * Metal implementation of IGpuRenderBuffer.
 *
 * In Metal, there is no separate renderbuffer concept — render targets are
 * just MTLTextures with the renderTarget usage flag. This class creates a
 * MTLTexture configured for render target use.
 */
public class MetalRenderBuffer extends TrackedObject implements IGpuRenderBuffer {
    private final int id;
    private final long textureHandle;

    public MetalRenderBuffer(long deviceHandle, int glFormat, int width, int height) {
        int metalFormat = MetalFormatUtil.glFormatToMetal(glFormat);

        long descriptor = MetalNative.mtlNewTextureDescriptor(
                MetalNative.MTLTextureType2D, metalFormat, width, height, 1,
                MetalNative.MTLTextureUsageRenderTarget | MetalNative.MTLTextureUsageShaderRead,
                MetalNative.MTLStorageModePrivate);

        this.textureHandle = MetalNative.mtlDeviceNewTexture(deviceHandle, descriptor);
        MetalNative.mtlRelease(descriptor);

        if (this.textureHandle == 0) {
            throw new RuntimeException("Failed to create Metal render buffer " + width + "x" + height);
        }
        this.id = MetalHandleMap.register(this.textureHandle);
    }

    @Override
    public int id() {
        return this.id;
    }

    /** Returns the underlying MTLTexture handle. */
    public long getTextureHandle() {
        return this.textureHandle;
    }

    @Override
    public void free() {
        super.free0();
        MetalHandleMap.unregister(this.id);
        MetalNative.mtlRelease(this.textureHandle);
    }
}
