package me.cortex.voxy.client.core.rendering.util;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.GlFence;
import me.cortex.voxy.client.core.gl.GlPersistentMappedBuffer;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.AllocationArena;
import me.cortex.voxy.common.util.MemoryBuffer;

import java.util.ArrayDeque;
import java.util.Deque;

import static me.cortex.voxy.common.util.AllocationArena.SIZE_LIMIT;
import static org.lwjgl.opengl.ARBDirectStateAccess.glCopyNamedBufferSubData;
import static org.lwjgl.opengl.ARBMapBufferRange.*;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL42.GL_UNIFORM_BARRIER_BIT;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL42C.GL_BUFFER_UPDATE_BARRIER_BIT;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL44.GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT;
import static org.lwjgl.opengl.GL44.GL_MAP_COHERENT_BIT;
import static org.lwjgl.opengl.GL45C.glFlushMappedNamedBufferRange;

public class UploadStream {
    private final AllocationArena allocationArena = new AllocationArena();
    private final GlPersistentMappedBuffer uploadBuffer;

    private final Deque<UploadFrame> frames = new ArrayDeque<>();
    private final LongArrayList thisFrameAllocations = new LongArrayList();
    private final Deque<UploadData> uploadList = new ArrayDeque<>();

    private static final boolean USE_COHERENT = false;

    public UploadStream(long size) {
        this.uploadBuffer = new GlPersistentMappedBuffer(size,GL_MAP_WRITE_BIT|GL_MAP_UNSYNCHRONIZED_BIT|(USE_COHERENT?GL_MAP_COHERENT_BIT:GL_MAP_FLUSH_EXPLICIT_BIT)).name("UploadStream");
        this.allocationArena.setLimit(size);
    }

    private long caddr = -1;
    private long offset = 0;
    public void upload(GlBuffer buffer, long destOffset, MemoryBuffer data) {//Note: does not free data, nor does it commit
        data.cpyTo(this.upload(buffer, destOffset, data.size));
    }

    public long upload(GlBuffer buffer, long destOffset, long size) {
        long addr = this.rawUploadAddress((int) size);

        this.uploadList.add(new UploadData(buffer, addr, destOffset, size));

        return this.uploadBuffer.addr() + addr;
    }

    public long rawUpload(int size) {
        return this.uploadBuffer.addr() + this.rawUploadAddress(size);
    }

    public long rawUploadAddress(int size) {
        if (size < 0) {
            throw new IllegalStateException("Negative size");
        }

        if (size > this.uploadBuffer.size()) {
            throw new IllegalArgumentException();
        }
        //Force natural size alignment, this should ensure that _all_ allocations are aligned to this size, note, this only effects the allocation block
        // not how much data is moved or copied
        size = (size+15)&~15;//Alignment to 16 bytes

        long addr;
        if (this.caddr == -1 || !this.allocationArena.expand(this.caddr, (int) size)) {
            if ((!USE_COHERENT)&&this.caddr!=-1) {
                glFlushMappedNamedBufferRange(this.uploadBuffer.id, this.caddr, this.offset);
            }
            this.caddr = this.allocationArena.alloc((int) size);//TODO: replace with allocFromLargest
            if (this.caddr == SIZE_LIMIT) {
                //Note! we dont commit here, we only try to flush existing memory copies, we dont commit
                // since commit is an explicit op saying we are done any to push upload everything
                //We dont commit since we dont want to invalidate existing upload pointers
                Logger.error("Upload stream full, preemptively committing, this could cause bad things to happen");
                int attempts = 10;
                while (--attempts != 0 && this.caddr == SIZE_LIMIT) {
                    glFinish();
                    this.tick(false);
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

        return addr;
    }

    public void commit() {
        if (this.uploadList.isEmpty()) {
            return;
        }
        if ((!USE_COHERENT)&&this.caddr != -1) {
            //Flush this allocation
            glFlushMappedNamedBufferRange(this.uploadBuffer.id, this.caddr, this.offset);
        }

        glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT);
        //Execute all the copies
        for (var entry : this.uploadList) {
            glCopyNamedBufferSubData(this.uploadBuffer.id, entry.target.id, entry.uploadOffset, entry.targetOffset, entry.size);
        }
        this.uploadList.clear();

        glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT);//|GL_SHADER_STORAGE_BARRIER_BIT|GL_UNIFORM_BARRIER_BIT //expected + other barriers which may cause issues if not

        this.caddr = -1;
        this.offset = 0;
    }

    public void tick() {
        this.tick(true);
    }
    private void tick(boolean commit) {
        if (commit) {
            this.commit();
        }

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

    public long getBaseAddress() {
        return this.uploadBuffer.addr();
    }

    public int getRawBufferId() {
        return this.uploadBuffer.id;
    }

    private record UploadFrame(GlFence fence, LongArrayList allocations) {}
    private record UploadData(GlBuffer target, long uploadOffset, long targetOffset, long size) {}

    //A upload instance instead of passing one around by reference
    // MUST ONLY BE USED ON THE RENDER THREAD
    public static final UploadStream INSTANCE = new UploadStream(1<<26);//64 mb upload buffer

}
