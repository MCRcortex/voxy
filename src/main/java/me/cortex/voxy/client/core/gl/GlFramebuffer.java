package me.cortex.voxy.client.core.gl;

import me.cortex.voxy.client.core.gpu.IGpuFramebuffer;
import me.cortex.voxy.client.core.gpu.IGpuRenderBuffer;
import me.cortex.voxy.client.core.gpu.IGpuTexture;
import me.cortex.voxy.common.util.TrackedObject;
import org.lwjgl.opengl.GL30C;

public class GlFramebuffer extends TrackedObject implements IGpuFramebuffer {
    public final int id;
    public GlFramebuffer() {
        this.id = GLCompat.createFramebuffer();
    }

    @Override
    public int id() { return this.id; }

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

    // IGpuFramebuffer interface methods (delegate to concrete-typed methods)
    @Override
    public IGpuFramebuffer bind(int attachment, IGpuTexture texture) {
        return this.bind(attachment, (GlTexture) texture);
    }

    @Override
    public IGpuFramebuffer bind(int attachment, IGpuTexture texture, int level) {
        return this.bind(attachment, (GlTexture) texture, level);
    }

    @Override
    public IGpuFramebuffer bind(int attachment, IGpuRenderBuffer buffer) {
        return this.bind(attachment, (GlRenderBuffer) buffer);
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
