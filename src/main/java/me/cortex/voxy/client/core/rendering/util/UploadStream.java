package me.cortex.voxy.client.core.rendering.util;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.GlFence;
import me.cortex.voxy.client.core.gl.GlPersistentMappedBuffer;
import me.cortex.voxy.client.core.util.AllocationArena;

import java.util.ArrayDeque;
import java.util.Deque;

import static me.cortex.voxy.client.core.util.AllocationArena.SIZE_LIMIT;
import static org.lwjgl.opengl.ARBDirectStateAccess.glCopyNamedBufferSubData;
import static org.lwjgl.opengl.ARBDirectStateAccess.glFlushMappedNamedBufferRange;
import static org.lwjgl.opengl.ARBMapBufferRange.*;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL42.GL_ALL_BARRIER_BITS;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL42C.GL_BUFFER_UPDATE_BARRIER_BIT;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL44.GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT;
import static org.lwjgl.opengl.GL44.GL_MAP_COHERENT_BIT;

public class UploadStream {
    private final AllocationArena allocationArena = new AllocationArena();
    private final GlPersistentMappedBuffer uploadBuffer;

    private final Deque<UploadFrame> frames = new ArrayDeque<>();
    private final LongArrayList thisFrameAllocations = new LongArrayList();
    private final Deque<UploadData> uploadList = new ArrayDeque<>();

    public UploadStream(long size) {
        this.uploadBuffer = new GlPersistentMappedBuffer(size,GL_MAP_WRITE_BIT|GL_MAP_UNSYNCHRONIZED_BIT|GL_MAP_COHERENT_BIT);
        this.allocationArena.setLimit(size);
    }

    private long caddr = -1;
    private long offset = 0;
    public long upload(GlBuffer buffer, long destOffset, long size) {
        if (destOffset<0) {
            throw new IllegalArgumentException();
        }
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

        if (this.caddr + size > this.uploadBuffer.size()) {
            throw new IllegalStateException();
        }

        this.uploadList.add(new UploadData(buffer, addr, destOffset, size));

        return this.uploadBuffer.addr() + addr;
    }


    public void commit() {
        //Execute all the copies
        for (var entry : this.uploadList) {
            glCopyNamedBufferSubData(this.uploadBuffer.id, entry.target.id, entry.uploadOffset, entry.targetOffset, entry.size);
        }
        this.uploadList.clear();

        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_BUFFER_UPDATE_BARRIER_BIT);

        this.caddr = -1;
        this.offset = 0;
    }

    public void tick() {
        this.commit();
        if (!this.thisFrameAllocations.isEmpty()) {
            this.frames.add(new UploadFrame(new GlFence(), new LongArrayList(this.thisFrameAllocations)));
            this.thisFrameAllocations.clear();
        }

        while (!this.frames.isEmpty()) {
            //Since the ordering of frames is the ordering of the gl commands if we encounter an unsignaled fence
            // all the other fences should also be unsignaled
            if (!this.frames.peek().fence.signaled()) {
                break;
            }
            //Release all the allocations from the frame
            var frame = this.frames.pop();
            frame.allocations.forEach(this.allocationArena::free);
            frame.fence.free();
        }
    }

    private record UploadFrame(GlFence fence, LongArrayList allocations) {}
    private record UploadData(GlBuffer target, long uploadOffset, long targetOffset, long size) {}

    //A upload instance instead of passing one around by reference
    // MUST ONLY BE USED ON THE RENDER THREAD
    public static final UploadStream INSTANCE = new UploadStream(1<<25);//32 mb upload buffer

}
