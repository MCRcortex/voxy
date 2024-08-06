package me.cortex.voxy.common.util;

import org.lwjgl.system.MemoryUtil;

public class MemoryBuffer extends TrackedObject {
    public final long address;
    public final long size;

    public MemoryBuffer(long size) {
        this.size = size;
        this.address = MemoryUtil.nmemAlloc(size);
    }

    private MemoryBuffer(long address, long size) {
        this.size = size;
        this.address = address;
    }

    public void cpyTo(long dst) {
        super.assertNotFreed();
        UnsafeUtil.memcpy(this.address, dst, this.size);
    }

    @Override
    public void free() {
        super.free0();
        MemoryUtil.nmemFree(this.address);
    }

    public MemoryBuffer copy() {
        var copy = new MemoryBuffer(this.size);
        this.cpyTo(copy.address);
        return copy;
    }

    //Creates a new MemoryBuffer, defunking this buffer and sets the size to be a subsize of the current size
    public MemoryBuffer subSize(long size) {
        if (size > this.size) {
            throw new IllegalArgumentException("Requested size larger than current size");
        }
        //Free the current object, but not the memory associated with it
        super.free0();

        return new MemoryBuffer(this.address, size);
    }
}
