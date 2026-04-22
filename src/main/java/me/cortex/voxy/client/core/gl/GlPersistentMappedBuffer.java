package me.cortex.voxy.client.core.gl;

import me.cortex.voxy.common.util.TrackedObject;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL45C;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL30C.GL_MAP_FLUSH_EXPLICIT_BIT;
import static org.lwjgl.opengl.GL30C.GL_MAP_INVALIDATE_BUFFER_BIT;
import static org.lwjgl.opengl.GL30C.GL_MAP_READ_BIT;
import static org.lwjgl.opengl.GL30C.GL_MAP_UNSYNCHRONIZED_BIT;
import static org.lwjgl.opengl.GL30C.GL_MAP_WRITE_BIT;
import static org.lwjgl.opengl.GL44C.GL_CLIENT_STORAGE_BIT;
import static org.lwjgl.opengl.GL44C.GL_MAP_COHERENT_BIT;
import static org.lwjgl.opengl.GL44C.GL_MAP_PERSISTENT_BIT;

public class GlPersistentMappedBuffer extends TrackedObject implements me.cortex.voxy.client.core.gpu.IGpuPersistentBuffer {
    public final int id;
    private final long size;
    private final long addr;

    @Override
    public int id() { return this.id; }

    public GlPersistentMappedBuffer(long size, int flags) {
        boolean hasDSA = GL.getCapabilities().GL_ARB_direct_state_access || GL.getCapabilities().OpenGL45;
        boolean hasBufferStorage = GL.getCapabilities().GL_ARB_buffer_storage;

        this.size = size;
        if (hasDSA && hasBufferStorage) {
            this.id = GL45C.glCreateBuffers();
            GL45C.glNamedBufferStorage(this.id, size, GL_MAP_PERSISTENT_BIT|(flags&(GL_MAP_COHERENT_BIT|GL_MAP_WRITE_BIT|GL_MAP_READ_BIT|GL_CLIENT_STORAGE_BIT)));
            this.addr = GL45C.nglMapNamedBufferRange(this.id, 0, size, (flags&(GL_MAP_WRITE_BIT|GL_MAP_READ_BIT|GL_MAP_UNSYNCHRONIZED_BIT|GL_MAP_FLUSH_EXPLICIT_BIT))|GL_MAP_PERSISTENT_BIT);
        } else {
            // 4.3 fallback: use bind-to-edit buffer + non-persistent mapping.
            this.id = GL15C.glGenBuffers();
            int prev = GL15C.glGetInteger(GL15C.GL_ARRAY_BUFFER_BINDING);
            GL15C.glBindBuffer(GL_ARRAY_BUFFER, this.id);
            GL15C.glBufferData(GL_ARRAY_BUFFER, size, GL_DYNAMIC_DRAW);
            int mapFlags = flags&(GL_MAP_WRITE_BIT|GL_MAP_READ_BIT|GL_MAP_UNSYNCHRONIZED_BIT|GL_MAP_FLUSH_EXPLICIT_BIT);
            // Invalidate to avoid unnecessary sync when possible.
            if ((mapFlags & GL_MAP_WRITE_BIT) != 0) {
                mapFlags |= GL_MAP_INVALIDATE_BUFFER_BIT;
            }
            this.addr = GL30C.nglMapBufferRange(GL_ARRAY_BUFFER, 0, size, mapFlags);
            GL15C.glBindBuffer(GL_ARRAY_BUFFER, prev);
        }
    }

    @Override
    public void free() {
        this.free0();
        boolean hasDSA = GL.getCapabilities().GL_ARB_direct_state_access || GL.getCapabilities().OpenGL45;
        boolean hasBufferStorage = GL.getCapabilities().GL_ARB_buffer_storage;
        if (hasDSA && hasBufferStorage) {
            GL45C.glUnmapNamedBuffer(this.id);
        } else {
            int prev = GL15C.glGetInteger(GL15C.GL_ARRAY_BUFFER_BINDING);
            GL15C.glBindBuffer(GL_ARRAY_BUFFER, this.id);
            GL15C.glUnmapBuffer(GL_ARRAY_BUFFER);
            GL15C.glBindBuffer(GL_ARRAY_BUFFER, prev);
        }
        glDeleteBuffers(this.id);
    }

    public long size() {
        return this.size;
    }

    public long addr() {
        return this.addr;
    }

    public GlPersistentMappedBuffer name(String name) {
        return GlDebug.name(name, this);
    }

    @Override
    public void flushRange(long offset, long length) {
        if (length <= 0) return;
        boolean hasDSA = GL.getCapabilities().GL_ARB_direct_state_access || GL.getCapabilities().OpenGL45;
        if (hasDSA) {
            GL45C.glFlushMappedNamedBufferRange(this.id, offset, length);
        } else {
            int prev = GL15C.glGetInteger(GL15C.GL_ARRAY_BUFFER_BINDING);
            GL15C.glBindBuffer(GL_ARRAY_BUFFER, this.id);
            GL30C.glFlushMappedBufferRange(GL_ARRAY_BUFFER, offset, length);
            GL15C.glBindBuffer(GL_ARRAY_BUFFER, prev);
        }
    }
}
