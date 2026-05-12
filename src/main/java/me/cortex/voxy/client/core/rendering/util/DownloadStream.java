package me.cortex.voxy.client.core.rendering.util;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import me.cortex.voxy.client.core.gpu.IGpuBuffer;
import me.cortex.voxy.client.core.gpu.IGpuFence;
import me.cortex.voxy.client.core.gpu.IGpuPersistentBuffer;
import me.cortex.voxy.client.core.gpu.RenderBackendFactory;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.AllocationArena;
import me.cortex.voxy.common.util.MemoryBuffer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.function.Consumer;

import static me.cortex.voxy.common.util.AllocationArena.SIZE_LIMIT;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL30C.GL_MAP_READ_BIT;
import static org.lwjgl.opengl.GL42.GL_BUFFER_UPDATE_BARRIER_BIT;
import static org.lwjgl.opengl.GL44.GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT;

public class DownloadStream {
    public interface DownloadResultConsumer {
        void consume(long ptr, long size);
    }

    private final AllocationArena allocationArena = new AllocationArena();
    private final IGpuPersistentBuffer downloadBuffer;

    private final Deque<DownloadFrame> frames = new ArrayDeque<>();
    private final LongArrayList thisFrameAllocations = new LongArrayList();
    private final Deque<DownloadData> downloadList = new ArrayDeque<>();
    private final ArrayList<DownloadData> thisFrameDownloadList = new ArrayList<>();

    public DownloadStream(long size) {
        this.downloadBuffer = RenderBackendFactory.get().createPersistentBuffer(size, GL_MAP_READ_BIT);//|GL_MAP_COHERENT_BIT
        this.allocationArena.setLimit(size);
    }

    private long caddr = -1;
    private long offset = 0;

    //Pulls the entire buffer from the gpu
    public void download(IGpuBuffer buffer, DownloadResultConsumer resultConsumer) {
        this.download(buffer, 0, buffer.size(), resultConsumer);
    }

    public void download(IGpuBuffer buffer, Consumer<MemoryBuffer> resultConsumer) {
        this.download(buffer, 0, buffer.size(), resultConsumer);
    }

    public void download(IGpuBuffer buffer, long downloadOffset, long size, Consumer<MemoryBuffer> consumer) {
        this.download(buffer, downloadOffset, size, (ptr,size2)-> {
            consumer.accept(MemoryBuffer.createUntrackedUnfreeableRawFrom(ptr, size));
        });
    }

    public void download(IGpuBuffer buffer, long downloadOffset, long size, DownloadResultConsumer resultConsumer) {
        if (size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException();
        }
        if (size <= 0) {
            throw new IllegalArgumentException();
        }
        if (downloadOffset+size > buffer.size()) {
            throw new IllegalArgumentException();
        }

        long addr;
        if (this.caddr == -1 || !this.allocationArena.expand(this.caddr, (int) size)) {
            this.caddr = this.allocationArena.alloc((int) size);//TODO: replace with allocFromLargest
            if (this.caddr == SIZE_LIMIT) {
                Logger.warn("Download stream full, preemptively committing, this could cause bad things to happen");
                this.commit();
                int attempts = 10;
                while (--attempts != 0 && this.caddr == SIZE_LIMIT) {
                    glFinish();
                    this.tick();
                    this.caddr = this.allocationArena.alloc((int) size);
                }
                if (this.caddr == SIZE_LIMIT) {
                    throw new IllegalStateException("Could not allocate memory segment big enough for upload even after force flush");
                }
            }
            this.thisFrameAllocations.add(this.caddr);
            this.offset = size;
            addr = this.caddr;
        } else {//Could expand the allocation so just update it
            addr = this.caddr + this.offset;
            this.offset += size;
        }

        if (this.caddr + size > this.downloadBuffer.size()) {
            throw new IllegalStateException();
        }

        this.downloadList.add(new DownloadData(buffer, addr, downloadOffset, size, resultConsumer));

        //TODO: maybe not auto-commit
        this.commit();
    }


    public void commit() {
        if (this.downloadList.isEmpty()) {
            return;
        }
        var backend = RenderBackendFactory.get();
        // Route through the backend so the Metal path doesn't call GL 4.2's
        // glMemoryBarrier on Apple's GL 4.1 context (which would
        // FATAL_ERROR in native method: No context is current).
        backend.memoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT);
        //Copies all the data from target buffers into the download stream
        for (var entry : this.downloadList) {
            backend.copyBufferSubData(entry.target, this.downloadBuffer,
                    entry.targetOffset, entry.downloadStreamOffset, entry.size);
        }
        backend.memoryBarrier(GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT | GL_BUFFER_UPDATE_BARRIER_BIT);
        this.thisFrameDownloadList.addAll(this.downloadList);
        this.downloadList.clear();

        this.caddr = -1;
        this.offset = 0;
    }

    public void tick() {
        this.commit();
        if (!this.thisFrameAllocations.isEmpty()) {
            this.frames.add(new DownloadFrame(RenderBackendFactory.get().createFence(), new LongArrayList(this.thisFrameAllocations), new ArrayList<>(this.thisFrameDownloadList)));
            this.thisFrameAllocations.clear();
            this.thisFrameDownloadList.clear();
        }

        while (!this.frames.isEmpty()) {
            //Since the ordering of frames is the ordering of the gl commands if we encounter an unsignaled fence
            // all the other fences should also be unsignaled
            if (!this.frames.peek().fence.signaled()) {
                break;
            }

            //Release all the allocations from the frame
            var frame = this.frames.pop();

            //Apply all the callbacks
            for (var data : frame.data) {
                data.resultConsumer.consume(this.downloadBuffer.addr() + data.downloadStreamOffset, data.size);
            }

            frame.allocations.forEach(this.allocationArena::free);
            frame.fence.free();
        }
    }

    //Synchonize force flushes everything
    public void waitDiscard() {
        glFinish();
        var fence = RenderBackendFactory.get().createFence();
        glFinish();
        while (!fence.signaled())
            Thread.onSpinWait();
        fence.free();
        while (!this.frames.isEmpty()) {
            var frame = this.frames.pop();
            while (!frame.fence.signaled()) Thread.onSpinWait();
            frame.allocations.forEach(this.allocationArena::free);
            frame.fence.free();
        }
    }

    public void flushWaitClear() {
        glFinish();
        this.tick();
        var fence = RenderBackendFactory.get().createFence();
        glFinish();
        while (!fence.signaled())
            Thread.onSpinWait();
        fence.free();
        this.tick();
        if (!this.frames.isEmpty()) {
            throw new IllegalStateException();
        }
    }

    private record DownloadFrame(IGpuFence fence, LongArrayList allocations, ArrayList<DownloadData> data) {}
    private record DownloadData(IGpuBuffer target, long downloadStreamOffset, long targetOffset, long size, DownloadResultConsumer resultConsumer) {}


    // Global download stream
    public static final DownloadStream INSTANCE = new DownloadStream(1<<25);//32 mb download buffer
}
