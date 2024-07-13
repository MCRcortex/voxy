package me.cortex.voxy.client.core.gl;

import me.cortex.voxy.common.util.TrackedObject;

import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL44C.glBufferStorage;
import static org.lwjgl.opengl.GL45C.*;

public class GlBuffer extends TrackedObject {
    public final int id;
    private final long size;

    public GlBuffer(long size) {
        this(size, 0);
    }

    public GlBuffer(long size, int flags) {
        this.id = glCreateBuffers();
        this.size = size;
        glNamedBufferStorage(this.id, size, flags);
    }

    @Override
    public void free() {
        this.free0();
        glDeleteBuffers(this.id);
    }

    public long size() {
        return this.size;
    }

    public GlBuffer zero() {
        nglClearNamedBufferData(this.id, GL_R8UI, GL_RED_INTEGER, GL_UNSIGNED_BYTE, 0);
        return this;
    }
}
