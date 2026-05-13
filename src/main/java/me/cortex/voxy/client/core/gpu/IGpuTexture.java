package me.cortex.voxy.client.core.gpu;

/**
 * Abstraction over a GPU texture resource.
 */
public interface IGpuTexture extends IGpuResource {
    int id();
    int getWidth();
    int getHeight();
    int getLevels();
    int getFormat();
    int getType();

    IGpuTexture store(int format, int levels, int width, int height);

    /**
     * Cross-backend variant of {@link #store} that requests CPU-uploadable
     * storage. On GL the call is identical to {@link #store} (no Private/
     * Shared distinction). On Metal it allocates with `MTLStorageModeShared`
     * and drops the `MTLTextureUsageRenderTarget` flag so subsequent
     * {@link #uploadSubImage2D} works.
     *
     * Default impl delegates to {@link #store} — only Metal needs to
     * override.
     */
    default IGpuTexture storeUploadable(int format, int levels, int width, int height) {
        return this.store(format, levels, width, height);
    }

    IGpuTexture createView();
    IGpuTexture name(String name);
    void assertAllocated();

    /**
     * Upload pixel data from {@code dataAddr} into the texture region
     * {@code (x, y, w, h)} at mip {@code level}. {@code format} and
     * {@code type} use OpenGL pixel-transfer constants (e.g. GL_RGBA +
     * GL_UNSIGNED_BYTE) describing the source layout; the bytes themselves
     * must already match what the texture's storage format expects.
     *
     * GL lowers to {@code glTextureSubImage2D}. Metal lowers to
     * {@code -[MTLTexture replaceRegion:mipmapLevel:withBytes:bytesPerRow:]} —
     * which requires the texture to have been created with Shared/Managed
     * storage (see {@code MetalTexture.storeUploadable}). The default
     * implementation throws — only textures backed by mutable, CPU-writable
     * storage need to support it.
     */
    default void uploadSubImage2D(int level, int x, int y, int w, int h,
                                  int format, int type, long dataAddr) {
        throw new UnsupportedOperationException(
                "uploadSubImage2D not supported on " + this.getClass().getName());
    }
}
