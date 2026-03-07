package me.cortex.voxy.client.core.gl;

import me.cortex.voxy.common.util.TrackedObject;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.ARBSparseBuffer.GL_SPARSE_STORAGE_BIT_ARB;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL15C.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL15C.glGetInteger;
import static org.lwjgl.opengl.GL30C.GL_R32UI;
import static org.lwjgl.opengl.GL30C.GL_R8UI;
import static org.lwjgl.opengl.GL30C.GL_RED_INTEGER;

public class GlBuffer extends TrackedObject implements me.cortex.voxy.client.core.gpu.IGpuBuffer {
    public final int id;
    private final long size;
    private final int flags;

    @Override
    public int id() { return this.id; }

    private static int COUNT;
    private static long TOTAL_SIZE;

    public GlBuffer(long size) {
        this(size, 0);
    }
    public GlBuffer(long size, boolean zero) {
        this(size, 0, zero);
    }

    public GlBuffer(long size, int flags) {
        this(size, flags, true);
    }

    public GlBuffer(long size, int flags, boolean zero) {
        this.flags = flags;
        this.size = size;

        boolean hasDSA = GL.getCapabilities().GL_ARB_direct_state_access || GL.getCapabilities().OpenGL45;
        boolean hasBufferStorage = GL.getCapabilities().GL_ARB_buffer_storage;
        boolean hasClearBuffer = GL.getCapabilities().GL_ARB_clear_buffer_object || GL.getCapabilities().OpenGL44;

        if (hasDSA) {
            this.id = GL45C.glCreateBuffers();
            if (hasBufferStorage) {
                GL45C.glNamedBufferStorage(this.id, size, flags);
            } else {
                GL45C.glNamedBufferData(this.id, size, GL_DYNAMIC_DRAW);
            }
            if ((flags & GL_SPARSE_STORAGE_BIT_ARB) == 0 && zero) {
                if (hasClearBuffer) {
                    GL45C.nglClearNamedBufferData(this.id, GL_R8UI, GL_RED_INTEGER, GL_UNSIGNED_BYTE, 0);
                } else {
                    zeroFallbackBound(GL_ARRAY_BUFFER, 0, size);
                }
            }
        } else {
            this.id = GL15C.glGenBuffers();
            int prev = glGetInteger(GL_ARRAY_BUFFER_BINDING);
            GL15C.glBindBuffer(GL_ARRAY_BUFFER, this.id);
            GL15C.glBufferData(GL_ARRAY_BUFFER, size, GL_DYNAMIC_DRAW);
            if ((flags & GL_SPARSE_STORAGE_BIT_ARB) == 0 && zero) {
                zeroFallbackBound(GL_ARRAY_BUFFER, 0, size);
            }
            GL15C.glBindBuffer(GL_ARRAY_BUFFER, prev);
        }

        COUNT++;
        TOTAL_SIZE += size;
    }

    @Override
    public void free() {
        this.free0();
        glDeleteBuffers(this.id);

        COUNT--;
        TOTAL_SIZE -= this.size;
    }

    public boolean isSparse() {
        return (this.flags&GL_SPARSE_STORAGE_BIT_ARB)!=0;
    }

    public long size() {
        return this.size;
    }

    public GlBuffer zero() {
        return zeroRange(0, this.size);
    }

    public GlBuffer zeroRange(long offset, long size) {
        boolean hasDSA = GL.getCapabilities().GL_ARB_direct_state_access || GL.getCapabilities().OpenGL45;
        boolean hasClearBuffer = GL.getCapabilities().GL_ARB_clear_buffer_object || GL.getCapabilities().OpenGL44;
        if (hasDSA && hasClearBuffer) {
            GL45C.nglClearNamedBufferSubData(this.id, GL_R8UI, offset, size, GL_RED_INTEGER, GL_UNSIGNED_BYTE, 0);
        } else {
            int prev = glGetInteger(GL_ARRAY_BUFFER_BINDING);
            GL15C.glBindBuffer(GL_ARRAY_BUFFER, this.id);
            zeroFallbackBound(GL_ARRAY_BUFFER, offset, size);
            GL15C.glBindBuffer(GL_ARRAY_BUFFER, prev);
        }
        return this;
    }

    public GlBuffer fill(int data) {
        //Clear unpack values
        //Fixed in mesa commit a5c3c452
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, 0);

        boolean hasDSA = GL.getCapabilities().GL_ARB_direct_state_access || GL.getCapabilities().OpenGL45;
        boolean hasClearBuffer = GL.getCapabilities().GL_ARB_clear_buffer_object || GL.getCapabilities().OpenGL44;
        if (hasDSA && hasClearBuffer) {
            MemoryUtil.memPutInt(SCRATCH, data);
            GL45C.nglClearNamedBufferData(this.id, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, SCRATCH);
        } else {
            int prev = glGetInteger(GL_ARRAY_BUFFER_BINDING);
            GL15C.glBindBuffer(GL_ARRAY_BUFFER, this.id);
            fillFallbackBound(GL_ARRAY_BUFFER, data, this.size);
            GL15C.glBindBuffer(GL_ARRAY_BUFFER, prev);
        }
        return this;
    }

    public static int getCount() {
        return COUNT;
    }

    public static long getTotalSize() {
        return TOTAL_SIZE;
    }

    public GlBuffer name(String name) {
        return GlDebug.name(name, this);
    }

    private void zeroFallbackBound(int target, long offset, long size) {
        long remaining = size;
        long off = offset;
        while (remaining > 0) {
            int chunk = (int) Math.min(remaining, ZERO_CHUNK_SIZE);
            ZERO_CHUNK.limit(chunk).position(0);
            GL15C.glBufferSubData(target, off, ZERO_CHUNK);
            remaining -= chunk;
            off += chunk;
        }
    }

    private void fillFallbackBound(int target, int value, long size) {
        long remaining = size;
        long off = 0;
        while (remaining > 0) {
            int chunk = (int) Math.min(remaining, VALUE_CHUNK.capacity());
            VALUE_CHUNK.clear();//Ensure limit covers full capacity while writing
            prepareValueChunk(value, chunk);
            VALUE_CHUNK.limit(chunk).position(0);
            GL15C.glBufferSubData(target, off, VALUE_CHUNK);
            remaining -= chunk;
            off += chunk;
        }
    }

    private void prepareValueChunk(int value, int bytes) {
        int ints = (bytes + 3) / 4;
        for (int i = 0; i < ints; i++) {
            VALUE_CHUNK.putInt(i * 4, value);
        }
    }

    private static final int ZERO_CHUNK_SIZE = 1 << 16;//64kb
    private static final ByteBuffer ZERO_CHUNK = MemoryUtil.memCalloc(ZERO_CHUNK_SIZE);
    private static final ByteBuffer VALUE_CHUNK = MemoryUtil.memAlloc(ZERO_CHUNK_SIZE);
    private static final long SCRATCH = MemoryUtil.nmemAlloc(4);
}
