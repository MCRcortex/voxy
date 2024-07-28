package me.cortex.voxy.client.core.rendering.util;

import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.common.util.UnsafeUtil;
import org.lwjgl.system.MemoryUtil;

//Just a utility for making a deferred upload (make on other thread then upload on render thread)
public final class DeferredUpload {
    public final long ptr;
    private final long size;
    private final long offset;
    private final GlBuffer buffer;
    public DeferredUpload(GlBuffer buffer, long offset, long size) {
        this.ptr = MemoryUtil.nmemAlloc(size);
        this.offset = offset;
        this.buffer = buffer;
        this.size = size;
    }

    public void upload() {
        long upPtr = UploadStream.INSTANCE.upload(this.buffer, this.offset, this.size);
        UnsafeUtil.memcpy(this.ptr, upPtr, this.size);
        MemoryUtil.nmemFree(this.ptr);
    }
}
