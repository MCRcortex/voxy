package me.cortex.voxy.client.core.gl;

import me.cortex.voxy.common.util.TrackedObject;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;

import java.util.Arrays;

import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.opengl.GL45C.*;

public class GlVertexArray extends TrackedObject {
    public static final int STATIC_VAO = glGenVertexArrays();

    public final int id;
    private int[] indices = new int[0];
    private int stride;
    public GlVertexArray() {
        this.id = glCreateVertexArrays();
    }

    @Override
    public void free() {
        this.free0();
        glDeleteVertexArrays(this.id);
    }

    public void bind() {
        glBindVertexArray(this.id);
    }

    public GlVertexArray bindBuffer(int buffer) {
        // Optimized: use glVertexArrayVertexBuffers to bind all indices in one call
        if (this.indices.length == 0) return this;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            int count = this.indices.length;
            int[] buffers = new int[count];
            PointerBuffer offsets = stack.mallocPointer(count);
            int[] strides = new int[count];

            for (int i = 0; i < count; i++) {
                buffers[i] = buffer;
                offsets.put(i, 0L);
                strides[i] = this.stride;
            }

            glVertexArrayVertexBuffers(this.id, this.indices[0], buffers, offsets, strides);
        }
        return this;
    }

    public GlVertexArray bindElementBuffer(int buffer) {
        glVertexArrayElementBuffer(this.id, buffer);
        return this;
    }

    public GlVertexArray setStride(int stride) {
        this.stride = stride;
        return this;
    }

    public GlVertexArray setI(int index, int type, int count, int offset) {
        this.addIndex(index);
        glEnableVertexArrayAttrib(this.id, index);
        glVertexArrayAttribIFormat(this.id, index, count, type, offset);
        return this;
    }

    public GlVertexArray setF(int index, int type, int count, int offset) {
        return this.setF(index, type, count, false, offset);
    }

    public GlVertexArray setF(int index, int type, int count, boolean normalize, int offset) {
        this.addIndex(index);
        glEnableVertexArrayAttrib(this.id, index);
        glVertexArrayAttribFormat(this.id, index, count, type, normalize, offset);
        return this;
    }

    private void addIndex(int index) {
        for (int i : this.indices) {
            if (i == index) {
                return;
            }
        }
        this.indices = Arrays.copyOf(this.indices, this.indices.length+1);
        this.indices[this.indices.length-1] = index;
    }
}
