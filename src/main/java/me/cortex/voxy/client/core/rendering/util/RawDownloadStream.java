package me.cortex.voxy.client.core.rendering.util;


import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.GlFence;
import me.cortex.voxy.client.core.gl.GlPersistentMappedBuffer;
import me.cortex.voxy.client.core.util.AllocationArena;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

import static org.lwjgl.opengl.ARBMapBufferRange.GL_MAP_READ_BIT;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL44.GL_MAP_COHERENT_BIT;

//Special download stream which allows access to the download buffer directly
public class RawDownloadStream {
    //NOTE: after the callback returns the pointer is no longer valid for client use
    public interface IDownloadCompletedCallback{void accept(long ptr);}
    private record DownloadFragment(int allocation, IDownloadCompletedCallback callback){}
    private record DownloadFrame(GlFence fence, DownloadFragment[] fragments) {}

    private final GlPersistentMappedBuffer downloadBuffer;
    private final AllocationArena allocationArena = new AllocationArena();
    private final ArrayList<DownloadFragment> frameFragments = new ArrayList<>();
    private final Deque<DownloadFrame> frames = new ArrayDeque<>();

    public RawDownloadStream(int size) {
        this.downloadBuffer = new GlPersistentMappedBuffer(size, GL_MAP_READ_BIT|GL_MAP_COHERENT_BIT);
        this.allocationArena.setLimit(size);
    }

    public int download(int size, IDownloadCompletedCallback callback) {
        int allocation = (int) this.allocationArena.alloc(size);
        if (allocation == AllocationArena.SIZE_LIMIT) {
            //Hit the download limit, attempt to free
            glFinish();
            this.tick();
            allocation = (int) this.allocationArena.alloc(size);
            if (allocation == AllocationArena.SIZE_LIMIT) {
                throw new IllegalStateException("Unable free enough memory for raw download stream");
            }
        }
        this.frameFragments.add(new DownloadFragment(allocation, callback));
        return allocation;
    }

    //Creates a new "frame" for previously allocated downloads and enqueues a fence
    // also invalidates all previous download pointers from this instance
    public void submit() {
        if (!this.frameFragments.isEmpty()) {
            var fragments = this.frameFragments.toArray(new DownloadFragment[0]);
            this.frameFragments.clear();
            this.frames.add(new DownloadFrame(new GlFence(), fragments));
        }
    }

    public void tick() {
        while (!this.frames.isEmpty()) {
            //If the first element is not signaled, none of the others will be signaled so break
            if (!this.frames.peek().fence.signaled()) {
                break;
            }
            var frame = this.frames.poll();
            for (var fragment : frame.fragments) {
                long addr = this.downloadBuffer.addr() + fragment.allocation;
                fragment.callback.accept(addr);
                this.allocationArena.free(fragment.allocation);
            }
            frame.fence.free();
        }
    }

    public int getBufferId() {
        return this.downloadBuffer.id;
    }

    public void free() {
        this.frames.forEach(a->a.fence.free());
        this.downloadBuffer.free();
    }
}
