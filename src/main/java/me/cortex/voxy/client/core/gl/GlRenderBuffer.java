package me.cortex.voxy.client.core.gl;

import me.cortex.voxy.common.util.TrackedObject;

import static me.cortex.voxy.client.core.gl.GLCompat.createRenderbuffer;
import static me.cortex.voxy.client.core.gl.GLCompat.renderbufferStorage;

public class GlRenderBuffer extends TrackedObject implements me.cortex.voxy.client.core.gpu.IGpuRenderBuffer {
    public final int id;

    @Override
    public int id() { return this.id; }

    public GlRenderBuffer(int format, int width, int height) {
        this.id = GLCompat.createRenderbuffer();
        GLCompat.renderbufferStorage(this.id, format, width, height);
    }

    @Override
    public void free() {
        super.free0();
        org.lwjgl.opengl.GL30C.glDeleteRenderbuffers(this.id);
    }
}
