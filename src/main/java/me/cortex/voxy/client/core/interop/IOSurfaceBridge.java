package me.cortex.voxy.client.core.interop;

import me.cortex.voxy.client.core.metal.MetalNative;

/**
 * macOS-specific bridge that pins a shared chunk of GPU memory between MC's
 * OpenGL context and Voxy's Metal rendering. Backed by an {@code IOSurface}
 * (the system framework purpose-built for cross-API zero-copy texture
 * sharing).
 *
 * Lifecycle:
 *  1. {@link #create(int, int, IOSurfaceFormat)} allocates the IOSurface and
 *     wraps it as an {@code MTLTexture}. Metal uses the texture as a render
 *     target.
 *  2. A future {@code bindToGl} (separate JNI in
 *     {@code voxy_metal_iosurface_gl.mm}) will hand the same IOSurface to
 *     {@code CGLTexImageIOSurface2D} so MC's GL context sees it as a
 *     normal GL texture. The compositing shader on MC's side samples it.
 *  3. Sync via the existing {@code MTLSharedEvent} on the Metal backend +
 *     a corresponding {@code GL_ARB_sync} fence on the GL side. Voxy's
 *     Metal render submits then signals; MC waits before sampling.
 *  4. {@link #close()} releases the Metal texture and the IOSurface.
 *
 * The class is intentionally minimal — the encoder API doesn't need new
 * concepts to consume an IOSurface-backed render target, just the
 * {@link me.cortex.voxy.client.core.gpu.IGpuTexture} that {@link #metalTexture()}
 * returns. From the caller's view it's a normal texture; the IOSurface
 * sharing is invisible until the GL-side bind kicks in.
 */
public final class IOSurfaceBridge implements AutoCloseable {

    /** Common color/depth formats Voxy will need from the bridge. */
    public enum IOSurfaceFormat {
        /**
         * 32-bit BGRA, one byte per channel. The format MC's framebuffer
         * (and most CoreVideo paths) use natively; matches MTLPixelFormatBGRA8Unorm.
         */
        BGRA8(MetalNative.IOSurfacePixelFormat_BGRA8, 4, /*MTLPixelFormatBGRA8Unorm*/ 80);

        final int ioSurfacePixelFormat;
        final int bytesPerElement;
        final int metalPixelFormat;

        IOSurfaceFormat(int ioSurfacePixelFormat, int bytesPerElement, int metalPixelFormat) {
            this.ioSurfacePixelFormat = ioSurfacePixelFormat;
            this.bytesPerElement = bytesPerElement;
            this.metalPixelFormat = metalPixelFormat;
        }
    }

    private final int width;
    private final int height;
    private final IOSurfaceFormat format;
    private long ioSurfaceHandle;
    private long metalTextureHandle;

    private IOSurfaceBridge(int width, int height, IOSurfaceFormat format,
                             long ioSurfaceHandle, long metalTextureHandle) {
        this.width = width;
        this.height = height;
        this.format = format;
        this.ioSurfaceHandle = ioSurfaceHandle;
        this.metalTextureHandle = metalTextureHandle;
    }

    /**
     * Allocate an IOSurface-backed pair of textures sized {@code width × height}.
     * {@code device} is the MTLDevice handle from
     * {@link me.cortex.voxy.client.core.metal.MetalRenderBackend#device()}.
     * Throws if either allocation fails.
     */
    public static IOSurfaceBridge create(long device, int width, int height, IOSurfaceFormat format) {
        if (device == 0) throw new IllegalArgumentException("device handle is 0");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("invalid size " + width + "x" + height);
        }
        long surface = MetalNative.iosurfaceCreate(width, height, format.ioSurfacePixelFormat, format.bytesPerElement);
        if (surface == 0) {
            throw new RuntimeException("IOSurfaceCreate returned NULL for "
                    + width + "x" + height + " format=" + format);
        }
        long tex = MetalNative.mtlDeviceNewTextureWithIOSurface(
                device, surface, format.metalPixelFormat, width, height,
                MetalNative.MTLTextureUsageRenderTarget | MetalNative.MTLTextureUsageShaderRead);
        if (tex == 0) {
            MetalNative.iosurfaceRelease(surface);
            throw new RuntimeException("newTextureWithDescriptor:iosurface: returned NULL for "
                    + width + "x" + height + " format=" + format);
        }
        return new IOSurfaceBridge(width, height, format, surface, tex);
    }

    public int width()   { return this.width; }
    public int height()  { return this.height; }
    public IOSurfaceFormat format() { return this.format; }

    /** Raw IOSurfaceRef handle. The GL-side bind (future) consumes this. */
    public long ioSurfaceHandle() { return this.ioSurfaceHandle; }

    /** Raw MTLTexture handle — the Metal-side render target. */
    public long metalTextureHandle() { return this.metalTextureHandle; }

    @Override
    public void close() {
        if (this.metalTextureHandle != 0) {
            MetalNative.mtlRelease(this.metalTextureHandle);
            this.metalTextureHandle = 0;
        }
        if (this.ioSurfaceHandle != 0) {
            MetalNative.iosurfaceRelease(this.ioSurfaceHandle);
            this.ioSurfaceHandle = 0;
        }
    }
}
