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
    public GlTexture() {
        this(GL_TEXTURE_2D);
    }

    public GlTexture(int type) {
        this.id = glCreateTextures(type);
        this.type = type;
    }

    public GlTexture store(int format, int levels, int width, int height) {
        if (this.type == GL_TEXTURE_2D) {
            glTextureStorage2D(this.id, levels, format, width, height);
        } else {
            throw new IllegalStateException("Unknown texture type");
        }
        return this;
    }

    @Override
    public void free() {
        super.free0();
        glDeleteTextures(this.id);
    }

    //TODO: FIXME, glGetTextureParameteri doesnt work
    public static int getRawTextureType(int texture) {
        if (!glIsTexture(texture)) {
            throw new IllegalStateException("Not texture");
        }
        int immFormat = glGetTextureParameteri(texture, GL_TEXTURE_IMMUTABLE_FORMAT);
        if (immFormat == 0) {
            throw new IllegalStateException("Texture: " + texture + " is not immutable");
        }
        return immFormat;
    }
}
