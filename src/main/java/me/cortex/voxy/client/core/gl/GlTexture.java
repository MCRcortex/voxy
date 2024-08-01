package me.cortex.voxy.client.core.gl;

import me.cortex.voxy.common.util.TrackedObject;

import static org.lwjgl.opengl.ARBFramebufferObject.glDeleteFramebuffers;
import static org.lwjgl.opengl.ARBFramebufferObject.glGenFramebuffers;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL30C.glGetIntegeri;
import static org.lwjgl.opengl.GL45C.*;

public class GlTexture extends TrackedObject {
    public final int id;
    private final int type;
    private int format;
    public GlTexture() {
        this(GL_TEXTURE_2D);
    }

    public GlTexture(int type) {
        this.id = glCreateTextures(type);
        this.type = type;
    }

    private GlTexture(int type, boolean useGenTypes) {
        if (useGenTypes) {
            this.id = glGenTextures();
        } else {
            this.id = glCreateTextures(type);
        }
        this.type = type;
    }

    public GlTexture store(int format, int levels, int width, int height) {
        this.format = format;
        if (this.type == GL_TEXTURE_2D) {
            glTextureStorage2D(this.id, levels, format, width, height);
        } else {
            throw new IllegalStateException("Unknown texture type");
        }
        return this;
    }

    public GlTexture createView() {
        var view = new GlTexture(this.type, true);
        glTextureView(view.id, this.type, this.id, this.format, 0, 1, 0, 1);
        return view;
    }

    @Override
    public void free() {
        super.free0();
        glDeleteTextures(this.id);
    }
}
