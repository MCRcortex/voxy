package me.cortex.voxy.client.core.model;

import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.GlTexture;

import static org.lwjgl.opengl.GL11.GL_RGBA8;

public class ModelStore {
    public static final int MODEL_SIZE = 64;
    private final GlBuffer modelBuffer;
    private final GlBuffer modelColourBuffer;
    private final GlTexture textures;

    public ModelStore(int modelTextureSize) {
        this.modelBuffer = new GlBuffer(MODEL_SIZE * (1<<16));
        this.modelColourBuffer = new GlBuffer(4 * (1<<16));
        this.textures = new GlTexture().store(GL_RGBA8, 4, modelTextureSize*3*256,modelTextureSize*2*256);
    }


    public void free() {
        this.modelBuffer.free();
        this.modelColourBuffer.free();
        this.textures.free();
    }
}
