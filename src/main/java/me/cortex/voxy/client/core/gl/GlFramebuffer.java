package me.cortex.voxy.client.core.gl;

import me.cortex.voxy.common.util.TrackedObject;
import org.lwjgl.opengl.GL30C;

public class GlFramebuffer extends TrackedObject {
    public final int id;
    public GlFramebuffer() {
        this.id = GLCompat.createFramebuffer();
    }

    public GlFramebuffer bind(int attachment, GlTexture texture) {
        return this.bind(attachment, texture, 0);
    }

    public GlFramebuffer bind(int attachment, GlTexture texture, int lvl) {
        GLCompat.framebufferTexture(this.id, attachment, texture.id, lvl, texture.getType());
        return this;
    }

    public GlFramebuffer bind(int attachment, GlRenderBuffer buffer) {
        GLCompat.framebufferRenderbuffer(this.id, attachment, buffer.id);
        return this;
    }

    public GlFramebuffer setDrawBuffers(int... buffers) {
        GLCompat.framebufferDrawBuffers(this.id, buffers);
        return this;
    }

    @Override
    public void free() {
        super.free0();
        GLCompat.deleteFramebuffer(this.id);
    }

    public GlFramebuffer verify() {
        int code;
        if ((code = GLCompat.checkFramebufferStatus(this.id)) != GL30C.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Framebuffer incomplete with error code: " + code);
        }
        return this;
    }


    public GlFramebuffer name(String name) {
        return GlDebug.name(name, this);
    }
}
