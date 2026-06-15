package me.cortex.neovoxy.client.core.gl;

import me.cortex.neovoxy.common.util.TrackedObject;

import static org.lwjgl.opengl.GL45C.*;

public class GlRenderBuffer extends TrackedObject {
    public final int id;

    public GlRenderBuffer(int format, int width, int height) {
        this.id = glCreateRenderbuffers();
        glNamedRenderbufferStorage(this.id, format, width, height);
    }

    @Override
    public void free() {
        super.free0();
        glDeleteRenderbuffers(this.id);
    }
}
