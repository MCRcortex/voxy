package me.cortex.voxy.client.core.model;

import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.GlTexture;

import static org.lwjgl.opengl.GL11.GL_RGBA8;

public class ModelStore {
    public static final int MODEL_SIZE = 64;
    final GlBuffer modelBuffer;
    final GlBuffer modelColourBuffer;
    final GlTexture textures;

    public ModelStore() {
        this.modelBuffer = new GlBuffer(MODEL_SIZE * (1<<16));
        this.modelColourBuffer = new GlBuffer(4 * (1<<16));
        this.textures = new GlTexture().store(GL_RGBA8, 4, ModelFactory.MODEL_TEXTURE_SIZE*3*256,ModelFactory.MODEL_TEXTURE_SIZE*2*256);
    }


    public void free() {
        this.modelBuffer.free();
        this.modelColourBuffer.free();
        this.textures.free();
    }
}
