package me.cortex.voxy.client.core.rendering.util;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.GlFence;
import me.cortex.voxy.client.core.gl.GlPersistentMappedBuffer;
import me.cortex.voxy.client.core.util.AllocationArena;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

import static me.cortex.voxy.client.core.util.AllocationArena.SIZE_LIMIT;
import static org.lwjgl.opengl.ARBDirectStateAccess.glCopyNamedBufferSubData;
import static org.lwjgl.opengl.ARBDirectStateAccess.glFlushMappedNamedBufferRange;
import static org.lwjgl.opengl.ARBMapBufferRange.*;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL42C.GL_BUFFER_UPDATE_BARRIER_BIT;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL44.GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT;
import static org.lwjgl.opengl.GL44.GL_MAP_COHERENT_BIT;

public class DownloadStream {
    public interface DownloadResultConsumer {
        void consume(long ptr, long size);
    }

    private final AllocationArena allocationArena = new AllocationArena();
    private final GlPersistentMappedBuffer downloadBuffer;

    private final Deque<DownloadFrame> frames = new ArrayDeque<>();
    private final LongArrayList thisFrameAllocations = new LongArrayList();
    private final Deque<DownloadData> downloadList = new ArrayDeque<>();
    private final ArrayList<DownloadData> thisFrameDownloadList = new ArrayList<>();

    public DownloadStream(long size) {
        this.downloadBuffer = new GlPersistentMappedBuffer(size, GL_MAP_READ_BIT|GL_MAP_COHERENT_BIT);
        this.allocationArena.setLimit(size);
    }

    private long caddr = -1;
    private long offset = 0;
    public void download(GlBuffer buffer, long destOffset, long size, DownloadResultConsumer resultConsumer) {
        if (size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException();
        }

        long addr;
        if (this.caddr == -1 || !this.allocationArena.expand(this.caddr, (int) size)) {
            this.caddr = this.allocationArena.alloc((int) size);//TODO: replace with allocFromLargest
            if (this.caddr == SIZE_LIMIT) {
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

        this.downloadList.add(new DownloadData(buffer, addr, destOffset, size, resultConsumer));
    }


    public void commit() {
        //Copies all the data from target buffers into the download stream
        for (var entry : this.downloadList) {
            glCopyNamedBufferSubData(entry.target.id, this.downloadBuffer.id, entry.targetOffset, entry.downloadStreamOffset, entry.size);
        }
        this.thisFrameDownloadList.addAll(this.downloadList);
        this.downloadList.clear();

        this.caddr = -1;
        this.offset = 0;
    }

    public void tick() {
        this.commit();
        if (!this.thisFrameAllocations.isEmpty()) {
            glMemoryBarrier(GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT | GL_BUFFER_UPDATE_BARRIER_BIT);
            this.frames.add(new DownloadFrame(new GlFence(), new LongArrayList(this.thisFrameAllocations), new ArrayList<>(this.thisFrameDownloadList)));
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

    private record DownloadFrame(GlFence fence, LongArrayList allocations, ArrayList<DownloadData> data) {}
    private record DownloadData(GlBuffer target, long downloadStreamOffset, long targetOffset, long size, DownloadResultConsumer resultConsumer) {}


    // Global download stream
    public static final DownloadStream INSTANCE = new DownloadStream(1<<25);//32 mb download buffer
}
