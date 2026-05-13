package me.cortex.voxy.client.core.metal;

import me.cortex.voxy.client.core.gpu.IGpuTexture;
import me.cortex.voxy.common.util.TrackedObject;

/**
 * Metal implementation of IGpuTexture backed by a MTLTexture.
 *
 * Metal textures are created via texture descriptors rather than the
 * allocate-then-storage pattern used by OpenGL. This class defers the
 * actual MTLTexture creation until store() is called, matching the
 * OpenGL lazy-allocation pattern.
 */
public class MetalTexture extends TrackedObject implements IGpuTexture {
    private final int id;
    private final long deviceHandle;
    private final int textureType;
    private long handle;
    private int format;
    private int width;
    private int height;
    private int levels;
    private boolean allocated;
    /**
     * Tracks the MTLStorageMode the texture was created with. Only Shared /
     * Managed textures can be the target of {@link #uploadSubImage2D}; for
     * the default Private path the upload would silently no-op on Apple
     * Silicon, so the method throws instead of giving callers stale data.
     */
    private int storageMode = MetalNative.MTLStorageModePrivate;

    private static int COUNT;
    private static long ESTIMATED_TOTAL_SIZE;

    /**
     * Creates a MetalTexture that is not yet allocated.
     * Call store() to allocate the underlying MTLTexture.
     */
    public MetalTexture(long deviceHandle, int textureType) {
        this.deviceHandle = deviceHandle;
        this.textureType = textureType;
        // Allocate a placeholder ID; the actual texture handle is set in store()
        this.id = MetalHandleMap.register(Long.MAX_VALUE - COUNT); // temporary sentinel
        COUNT++;
    }

    @Override
    public int id() {
        return this.id;
    }

    @Override
    public int getWidth() {
        assertAllocated();
        return this.width;
    }

    @Override
    public int getHeight() {
        assertAllocated();
        return this.height;
    }

    @Override
    public int getLevels() {
        assertAllocated();
        return this.levels;
    }

    @Override
    public int getFormat() {
        assertAllocated();
        return this.format;
    }

    @Override
    public int getType() {
        return this.textureType;
    }

    @Override
    public IGpuTexture store(int format, int levels, int width, int height) {
        return allocate(format, levels, width, height,
                MetalNative.MTLTextureUsageShaderRead
                        | MetalNative.MTLTextureUsageShaderWrite
                        | MetalNative.MTLTextureUsageRenderTarget,
                MetalNative.MTLStorageModePrivate);
    }

    /**
     * Allocate the underlying MTLTexture with Shared storage and read-only
     * sampler usage — the layout {@link #uploadSubImage2D} requires.
     *
     * Use this instead of {@link #store} for textures that get their pixels
     * from CPU each frame (e.g. M13 chunk 2's MC-lightmap mirror). Shared
     * storage is unified-memory on Apple Silicon, so the upload is a
     * memcpy; render-target usage is intentionally dropped because the
     * texture is sampled-only, and dropping the flag avoids the Metal
     * driver's "render target may be evicted" overhead.
     */
    public IGpuTexture storeUploadable(int format, int levels, int width, int height) {
        return allocate(format, levels, width, height,
                MetalNative.MTLTextureUsageShaderRead,
                MetalNative.MTLStorageModeShared);
    }

    private IGpuTexture allocate(int format, int levels, int width, int height,
                                  int usage, int storageMode) {
        if (this.allocated) {
            throw new IllegalStateException("Texture already allocated");
        }
        this.format = format;
        this.width = width;
        this.height = height;
        this.levels = levels;
        this.storageMode = storageMode;
        this.allocated = true;

        int metalPixelFormat = MetalFormatUtil.glFormatToMetal(format);
        int metalTextureType = MetalFormatUtil.glTextureTypeToMetal(this.textureType);

        long descriptor = MetalNative.mtlNewTextureDescriptor(
                metalTextureType, metalPixelFormat, width, height, levels,
                usage, storageMode);

        this.handle = MetalNative.mtlDeviceNewTexture(this.deviceHandle, descriptor);
        MetalNative.mtlRelease(descriptor);

        if (this.handle == 0) {
            throw new RuntimeException("Failed to create Metal texture " + width + "x" + height);
        }

        // Replace the sentinel handle the constructor reserved with the real
        // MTLTexture handle, keeping the int id() stable so callers that
        // captured the id pre-store() still resolve correctly.
        MetalHandleMap.setHandle(this.id, this.handle);

        ESTIMATED_TOTAL_SIZE += estimateSize();
        return this;
    }

    @Override
    public void uploadSubImage2D(int level, int x, int y, int w, int h,
                                  int format, int type, long dataAddr) {
        assertAllocated();
        if (this.storageMode == MetalNative.MTLStorageModePrivate) {
            throw new IllegalStateException(
                    "uploadSubImage2D requires Shared/Managed storage — allocate via storeUploadable()");
        }
        int bpp = (int) MetalFormatUtil.bytesPerPixel(this.format);
        MetalNative.mtlTextureReplaceRegion(this.handle, level, x, y, w, h, dataAddr, w * bpp);
    }

    @Override
    public IGpuTexture createView() {
        assertAllocated();
        long viewHandle = MetalNative.mtlTextureNewView(this.handle,
                MetalFormatUtil.glFormatToMetal(this.format));
        if (viewHandle == 0) {
            throw new RuntimeException("Failed to create Metal texture view");
        }
        MetalTexture view = new MetalTexture(this.deviceHandle, this.textureType);
        view.handle = viewHandle;
        view.format = this.format;
        view.width = this.width;
        view.height = this.height;
        view.levels = this.levels;
        view.allocated = true;
        return view;
    }

    @Override
    public IGpuTexture name(String name) {
        assertAllocated();
        MetalNative.mtlSetLabel(this.handle, name);
        return this;
    }

    @Override
    public void assertAllocated() {
        if (!this.allocated) {
            throw new IllegalStateException("Texture not yet allocated");
        }
    }

    @Override
    public void free() {
        if (this.allocated) {
            ESTIMATED_TOTAL_SIZE -= estimateSize();
        }
        COUNT--;
        this.allocated = false;
        super.free0();
        MetalHandleMap.unregister(this.id);
        if (this.handle != 0) {
            MetalNative.mtlRelease(this.handle);
            this.handle = 0;
        }
    }

    /** Returns the native MTLTexture handle. */
    public long getHandle() {
        return this.handle;
    }

    private long estimateSize() {
        long elemSize = MetalFormatUtil.bytesPerPixel(this.format);
        long size = 0;
        for (int lvl = 0; lvl < this.levels; lvl++) {
            size += Math.max(((long) this.width) >> lvl, 1)
                    * Math.max(((long) this.height) >> lvl, 1)
                    * elemSize;
        }
        return size;
    }

    public static int getCount() {
        return COUNT;
    }

    public static long getEstimatedTotalSize() {
        return ESTIMATED_TOTAL_SIZE;
    }
}
