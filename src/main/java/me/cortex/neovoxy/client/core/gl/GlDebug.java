package me.cortex.neovoxy.client.core.gl;

import me.cortex.neovoxy.client.core.gl.shader.Shader;

import static org.lwjgl.opengl.GL43C.*;

public class GlDebug {
    public static final boolean GL_DEBUG = System.getProperty("neovoxy.glDebug", "false").equals("true");


    public static void push() {
        //glPushDebugGroup()
    }

    public static GlBuffer name(String name, GlBuffer buffer) {
        if (GL_DEBUG) {
            glObjectLabel(GL_BUFFER, buffer.id, name);
        }
        return buffer;
    }

    public static <T extends Shader> T name(String name, T shader) {
        if (GL_DEBUG) {
            glObjectLabel(GL_PROGRAM, shader.id(), name);
        }
        return shader;
    }

    public static GlFramebuffer name(String name, GlFramebuffer framebuffer) {
        if (GL_DEBUG) {
            glObjectLabel(GL_FRAMEBUFFER, framebuffer.id, name);
        }
        return framebuffer;
    }

    public static GlTexture name(String name, GlTexture texture) {
        if (GL_DEBUG) {
            glObjectLabel(GL_TEXTURE, texture.id, name);
        }
        return texture;
    }

    public static GlPersistentMappedBuffer name(String name, GlPersistentMappedBuffer buffer) {
        if (GL_DEBUG) {
            glObjectLabel(GL_BUFFER, buffer.id, name);
        }
        return buffer;
    }
}
