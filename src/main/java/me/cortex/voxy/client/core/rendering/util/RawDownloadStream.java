package me.cortex.voxy.client.core.rendering.util;


import me.cortex.voxy.client.core.gpu.IGpuFence;
import me.cortex.voxy.client.core.gpu.IGpuPersistentBuffer;
import me.cortex.voxy.client.core.gpu.RenderBackendFactory;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.AllocationArena;

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
    private record DownloadFrame(IGpuFence fence, DownloadFragment[] fragments) {}

    private final IGpuPersistentBuffer downloadBuffer;
    private final AllocationArena allocationArena = new AllocationArena();
    private final ArrayList<DownloadFragment> frameFragments = new ArrayList<>();
    private final Deque<DownloadFrame> frames = new ArrayDeque<>();

    public RawDownloadStream(int size) {
        this.downloadBuffer = RenderBackendFactory.get().createPersistentBuffer(size, GL_MAP_READ_BIT|GL_MAP_COHERENT_BIT).name("RawDownloadStream");
        this.allocationArena.setLimit(size);
    }

    public int download(int size, IDownloadCompletedCallback callback) {
        int allocation = (int) this.allocationArena.alloc(size);
        if (allocation == AllocationArena.SIZE_LIMIT) {
            Logger.warn("Raw download stream full, preemptively committing, this could cause bad things to happen");
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
            this.frames.add(new DownloadFrame(RenderBackendFactory.get().createFence(), fragments));
        }
    }

    public void tick() {
        this.submit();

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
        return this.downloadBuffer.id();
    }

    /**
     * CPU-mapped base address of the persistent download buffer. Callers can
     * write directly here (e.g. M13 chunk 1's CPU-readback bakery path on
     * Metal — `glGetTexImage` into scratch + memcpy to `getBufferAddr() +
     * allocation`). Coherent mapping means writes are immediately visible to
     * GPU; for the bakery callback flow the callback runs after the fence
     * signals so CPU-visible ordering is sufficient here.
     */
    public long getBufferAddr() {
        return this.downloadBuffer.addr();
    }

    public void free() {
        glFinish();
        this.tick();
        IGpuFence fence = RenderBackendFactory.get().createFence();
        while (!fence.signaled()) {
            glFinish();
        }
        fence.free();
        this.tick();
        if (this.frames.size() != 0) {
            throw new IllegalStateException();
        }
        this.frames.forEach(a->a.fence.free());
        this.downloadBuffer.free();
    }
}
