package me.cortex.voxy.client.core.metal;

import me.cortex.voxy.client.core.gpu.IGpuPersistentBuffer;
import me.cortex.voxy.common.util.TrackedObject;

/**
 * Metal implementation of IGpuPersistentBuffer.
 *
 * On Apple Silicon with unified memory, MTLBuffers in shared storage mode
 * are inherently "persistent mapped" — the CPU and GPU share the same
 * physical memory, and the contents pointer is always valid. This maps
 * naturally to OpenGL's persistent mapped buffer concept.
 *
 * No explicit map/unmap is needed; the buffer contents pointer is valid
 * for the lifetime of the buffer.
 */
public class MetalPersistentBuffer extends TrackedObject implements IGpuPersistentBuffer {
    private final int id;
    private final long handle;
    private final long size;
    private final long addr;

    /**
     * Creates a persistently mapped Metal buffer.
     * @param device native MTLDevice handle
     * @param size buffer size in bytes
     * @param flags GL-compatible flags (ignored for Metal, always shared storage)
     */
    public MetalPersistentBuffer(long device, long size, int flags) {
        this.size = size;

        // Shared storage mode ensures CPU-visible memory on Apple Silicon
        this.handle = MetalNative.mtlDeviceNewBuffer(device, size,
                MetalNative.MTLResourceStorageModeShared);
        if (this.handle == 0) {
            throw new RuntimeException("Failed to create Metal persistent buffer of size " + size);
        }

        this.addr = MetalNative.mtlBufferContents(this.handle);
        if (this.addr == 0) {
            MetalNative.mtlRelease(this.handle);
            throw new RuntimeException("Failed to get contents pointer for Metal persistent buffer");
        }

        this.id = MetalHandleMap.register(this.handle);
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
    public long addr() {
        return this.addr;
    }

    @Override
    public IGpuPersistentBuffer name(String name) {
        MetalNative.mtlSetLabel(this.handle, name);
        return this;
    }

    @Override
    public void flushRange(long offset, long length) {
        // MTLStorageModeShared on Apple Silicon is coherent unified memory —
        // CPU writes are visible to the GPU without an explicit flush.
        // (If we ever switch to MTLStorageModeManaged we will need to call
        // mtlBufferDidModifyRange here.)
    }

    @Override
    public void free() {
        this.free0();
        MetalHandleMap.unregister(this.id);
        MetalNative.mtlRelease(this.handle);
    }

    /** Returns the native MTLBuffer handle. */
    public long getHandle() {
        return this.handle;
    }
}
