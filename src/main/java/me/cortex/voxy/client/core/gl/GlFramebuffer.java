package me.cortex.voxy.client.core.gl;

import me.cortex.voxy.common.util.TrackedObject;

import static org.lwjgl.opengl.GL45C.*;
import static org.lwjgl.opengl.GL45C.glNamedFramebufferDrawBuffers;

public class GlFramebuffer extends TrackedObject {
    public final int id;
    public GlFramebuffer() {
        this.id = glCreateFramebuffers();
    }

    public GlFramebuffer bind(int attachment, GlTexture texture) {
        return this.bind(attachment, texture, 0);
    }

    public GlFramebuffer bind(int attachment, GlTexture texture, int lvl) {
        glNamedFramebufferTexture(this.id, attachment, texture.id, lvl);
        return this;
    }

    public GlFramebuffer bind(int attachment, GlRenderBuffer buffer) {
        glNamedFramebufferRenderbuffer(this.id, attachment, GL_RENDERBUFFER, buffer.id);
        return this;
    }

    public GlFramebuffer setDrawBuffers(int... buffers) {
        glNamedFramebufferDrawBuffers(this.id, buffers);
        return this;
    }

    @Override
    public void free() {
        super.free0();
        glDeleteFramebuffers(this.id);
    }

    public GlFramebuffer verify() {
        int code;
        if ((code = glCheckNamedFramebufferStatus(this.id, GL_FRAMEBUFFER)) != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Framebuffer incomplete with error code: " + code);
        }
        return this;
    }


    public GlFramebuffer name(String name) {
        return GlDebug.name(name, this);
    }
}
