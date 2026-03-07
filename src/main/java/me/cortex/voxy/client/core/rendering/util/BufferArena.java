package me.cortex.voxy.client.core.rendering.util;

import me.cortex.voxy.client.core.gl.Capabilities;
import me.cortex.voxy.client.core.gpu.IGpuBuffer;
import me.cortex.voxy.client.core.gpu.RenderBackendFactory;
import me.cortex.voxy.common.util.AllocationArena;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.util.UnsafeUtil;
import me.cortex.voxy.commonImpl.VoxyCommon;

import java.util.function.Consumer;

public class BufferArena {
    private static final boolean CHECK_SSBO_MAX_SIZE_CHECK = VoxyCommon.isVerificationFlagOn("checkSSBOMaxSize");

    private final long size;
    private final int elementSize;
    private final IGpuBuffer buffer;
    private final AllocationArena allocationMap = new AllocationArena();
    private long used;

    //TODO: cache the GlBuffer accross open and closing of the renderer
    // until the instance is closed, this helps the driver as allocating a huge block of memory is expensive
    // so reusing it is ideal
    public BufferArena(long capacity, int elementSize) {
        if (capacity%elementSize != 0) {
            throw new IllegalArgumentException("Capacity not a multiple of element size");
        }
        if (CHECK_SSBO_MAX_SIZE_CHECK && capacity > Capabilities.INSTANCE.ssboMaxSize) {
            throw new IllegalArgumentException("Buffer is bigger than max ssbo size (requested " + capacity + " but has max of " + Capabilities.INSTANCE.ssboMaxSize+")");
        }
        this.size = capacity;
        this.elementSize = elementSize;
        this.buffer = RenderBackendFactory.get().createBuffer(capacity);
        this.allocationMap.setLimit(capacity/elementSize);
    }

    public long upload(MemoryBuffer buffer) {
        if (buffer.size%this.elementSize!=0) {
            throw new IllegalArgumentException("Buffer size not multiple of elementSize");
        }
        int size = (int) (buffer.size/this.elementSize);
        long addr = this.allocationMap.alloc(size);
        if (addr == -1) {
            return -1;
        }
        long uploadPtr = UploadStream.INSTANCE.upload(this.buffer, addr * this.elementSize, buffer.size);
        UnsafeUtil.memcpy(buffer.address, uploadPtr, buffer.size);
        this.used += size;
        return addr;
    }

    public void free(long allocation) {
        this.used -= this.allocationMap.free(allocation);
    }

    public void free() {
        this.buffer.free();
    }

    public int id() {
        this.buffer.assertNotFreed();
        return this.buffer.id();
    }

    public float usage() {
        return (float) ((double)this.used/(this.buffer.size()/this.elementSize));
    }

    public long getUsedBytes() {
        return this.used*this.elementSize;
    }

    public void downloadRemove(long allocation, Consumer<MemoryBuffer> consumer) {
        int size = this.allocationMap.free(allocation);
        this.used -= size;
        DownloadStream.INSTANCE.download(this.buffer, allocation*this.elementSize, (long) size *this.elementSize, consumer);
    }
}
