package me.cortex.voxy.client.core.metal;

import me.cortex.voxy.client.core.gpu.IGpuBuffer;
import me.cortex.voxy.common.util.TrackedObject;

/**
 * Metal implementation of IGpuBuffer backed by a MTLBuffer.
 *
 * On Apple Silicon, all MTLBuffers with shared storage mode are CPU-visible
 * (unified memory architecture), so zero/fill operations can be done directly
 * via the contents pointer without needing blit encoders.
 */
public class MetalBuffer extends TrackedObject implements IGpuBuffer {
    private final int id;
    private final long handle;
    private final long size;
    private final long contentsPtr;

    private static int COUNT;
    private static long TOTAL_SIZE;

    /**
     * Creates a new Metal buffer.
     * @param device native MTLDevice handle
     * @param size buffer size in bytes
     * @param options Metal resource options (storage mode, etc.)
     * @param zero if true, zero-fill the buffer after creation
     */
    public MetalBuffer(long device, long size, int options, boolean zero) {
        this.size = size;
        this.handle = MetalNative.mtlDeviceNewBuffer(device, size, options);
        if (this.handle == 0) {
            throw new RuntimeException("Failed to create Metal buffer of size " + size);
        }
        this.id = MetalHandleMap.register(this.handle);

        // On shared/managed storage, we can get the CPU pointer
        this.contentsPtr = MetalNative.mtlBufferContents(this.handle);

        if (zero && this.contentsPtr != 0) {
            MetalNative.memsetZero(this.contentsPtr, size);
        }

        COUNT++;
        TOTAL_SIZE += size;
    }

    /** Native MTLBuffer handle (package-private for backend-internal use). */
    long handle() {
        return this.handle;
    }

    @Override
    public int id() {
        return this.id;
    }

    @Override
    public long size() {
        return this.size;
    }

    @Override
    public boolean isSparse() {
        return false; // Metal doesn't have sparse buffers in the OpenGL sense
    }

    @Override
    public IGpuBuffer zero() {
        return this.zeroRange(0, this.size);
    }

    @Override
    public IGpuBuffer zeroRange(long offset, long size) {
        if (this.contentsPtr != 0) {
            MetalNative.memsetZero(this.contentsPtr + offset, size);
            MetalNative.mtlBufferDidModifyRange(this.handle, offset, size);
        }
        return this;
    }

    @Override
    public IGpuBuffer fill(int data) {
        if (this.contentsPtr != 0) {
            MetalNative.memsetInt(this.contentsPtr, data, this.size / 4);
            MetalNative.mtlBufferDidModifyRange(this.handle, 0, this.size);
        }
        return this;
    }

    @Override
    public IGpuBuffer name(String name) {
        MetalNative.mtlSetLabel(this.handle, name);
        return this;
    }

    @Override
    public void free() {
        this.free0();
        MetalHandleMap.unregister(this.id);
        MetalNative.mtlRelease(this.handle);
        COUNT--;
        TOTAL_SIZE -= this.size;
    }

    /** Returns the native MTLBuffer handle. */
    public long getHandle() {
        return this.handle;
    }

    /** Returns the CPU-accessible pointer, or 0 for private storage. */
    public long getContentsPtr() {
        return this.contentsPtr;
    }

    public static int getCount() {
        return COUNT;
    }

    public static long getTotalSize() {
        return TOTAL_SIZE;
    }
}
