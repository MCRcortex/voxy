package me.cortex.voxelmon.core.gl;

import me.cortex.voxelmon.core.util.TrackedObject;

import static org.lwjgl.opengl.ARBFramebufferObject.*;
import static org.lwjgl.opengl.GL45C.glCreateFramebuffers;
import static org.lwjgl.opengl.GL45C.glNamedFramebufferTexture;

public class GlFramebuffer extends TrackedObject {
    public final int id;
    public GlFramebuffer() {
        this.id = glCreateFramebuffers();
    }

    public GlFramebuffer bind(int attachment, GlTexture texture) {
        glNamedFramebufferTexture(this.id, attachment, texture.id, 0);
        return this;
    }

    @Override
    public void free() {
        super.free0();
        glDeleteFramebuffers(this.id);
    }
}
