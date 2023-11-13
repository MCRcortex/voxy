package me.cortex.voxelmon.core.util;

import org.lwjgl.system.MemoryUtil;

public class MemoryBuffer extends TrackedObject {
    public final long address;
    public final long size;

    public MemoryBuffer(long size) {
        this.size = size;
        this.address = MemoryUtil.nmemAlloc(size);
    }

    public void cpyTo(long dst) {
        super.assertNotFreed();
        MemoryUtil.memCopy(this.address, dst, this.size);
    }

    @Override
    public void free() {
        super.free0();
        MemoryUtil.nmemFree(this.address);
    }
}
