package me.cortex.voxy.client.core.gl;

import me.cortex.voxy.common.util.TrackedObject;
import org.lwjgl.opengl.GL;

import java.util.Arrays;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL20C.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20C.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30C.glVertexAttribIPointer;
import static org.lwjgl.opengl.GL45C.*;

public class GlVertexArray extends TrackedObject implements me.cortex.voxy.client.core.gpu.IGpuVertexArray {
    public static final int STATIC_VAO = glGenVertexArrays();

    public final int id;
    private int[] indices = new int[0];
    private int stride;

    @Override
    public int id() { return this.id; }

    public GlVertexArray() {
        boolean hasDSA = GL.getCapabilities().GL_ARB_direct_state_access || GL.getCapabilities().OpenGL45;
        if (hasDSA) {
            this.id = glCreateVertexArrays();
        } else {
            this.id = glGenVertexArrays();
        }
    }

    @Override
    public void free() {
        this.free0();
        glDeleteVertexArrays(this.id);
    }

    @Override
    public void bind() {
        glBindVertexArray(this.id);
    }

    public GlVertexArray bindBuffer(int buffer) {
        boolean hasDSA = GL.getCapabilities().GL_ARB_direct_state_access || GL.getCapabilities().OpenGL45;
        if (hasDSA) {
            //TODO: optimization, use glVertexArrayVertexBuffers
            for (int index : this.indices) {
                glVertexArrayVertexBuffer(this.id, index, buffer, 0, this.stride);
            }
        } else {
            glBindVertexArray(this.id);
            glBindBuffer(GL_ARRAY_BUFFER, buffer);
        }
        return this;
    }

    public GlVertexArray bindElementBuffer(int buffer) {
        boolean hasDSA = GL.getCapabilities().GL_ARB_direct_state_access || GL.getCapabilities().OpenGL45;
        if (hasDSA) {
            glVertexArrayElementBuffer(this.id, buffer);
        } else {
            glBindVertexArray(this.id);
            glBindBuffer(org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER, buffer);
        }
        return this;
    }

    public GlVertexArray setStride(int stride) {
        this.stride = stride;
        return this;
    }

    public GlVertexArray setI(int index, int type, int count, int offset) {
        this.addIndex(index);
        boolean hasDSA = GL.getCapabilities().GL_ARB_direct_state_access || GL.getCapabilities().OpenGL45;
        if (hasDSA) {
            glEnableVertexArrayAttrib(this.id, index);
            glVertexArrayAttribIFormat(this.id, index, count, type, offset);
        } else {
            glBindVertexArray(this.id);
            glEnableVertexAttribArray(index);
            // Integer attribute pointer uses glVertexAttribIPointer
            glVertexAttribIPointer(index, count, type, this.stride, offset);
        }
        return this;
    }

    public GlVertexArray setF(int index, int type, int count, int offset) {
        return this.setF(index, type, count, false, offset);
    }

    public GlVertexArray setF(int index, int type, int count, boolean normalize, int offset) {
        this.addIndex(index);
        boolean hasDSA = GL.getCapabilities().GL_ARB_direct_state_access || GL.getCapabilities().OpenGL45;
        if (hasDSA) {
            glEnableVertexArrayAttrib(this.id, index);
            glVertexArrayAttribFormat(this.id, index, count, type, normalize, offset);
        } else {
            glBindVertexArray(this.id);
            glEnableVertexAttribArray(index);
            int glType = type == GL_FLOAT ? GL_FLOAT : type;
            glVertexAttribPointer(index, count, glType, normalize, this.stride, offset);
        }
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
