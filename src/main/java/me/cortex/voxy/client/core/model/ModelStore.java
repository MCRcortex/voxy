package me.cortex.voxy.client.core.model;

import me.cortex.voxy.client.core.gpu.IGpuBuffer;
import me.cortex.voxy.client.core.gpu.IGpuTexture;
import me.cortex.voxy.client.core.gpu.RenderBackendFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_NEAREST_MIPMAP_LINEAR;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_MAX_LOD;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_MIN_LOD;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.GL33C.glSamplerParameteri;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;
import static me.cortex.voxy.client.core.gl.GLCompat.bindTextureUnit;

public class ModelStore {
    public static final int MODEL_SIZE = 64;
    final IGpuBuffer modelBuffer;
    final IGpuBuffer modelColourBuffer;
    final IGpuTexture textures;
    public final int blockSampler = glGenSamplers();

    public ModelStore() {
        this.modelBuffer = RenderBackendFactory.get().createBuffer(MODEL_SIZE * (1<<16));
        this.modelColourBuffer = RenderBackendFactory.get().createBuffer(4 * (1<<16));
        this.textures = RenderBackendFactory.get().createTexture().store(GL_RGBA8, Integer.numberOfTrailingZeros(ModelFactory.MODEL_TEXTURE_SIZE), ModelFactory.MODEL_TEXTURE_SIZE*3*256,ModelFactory.MODEL_TEXTURE_SIZE*2*256).name("ModelTextures");


        //Limit the mips of the texture to match that of the terrain atlas
        int mipLvl = ((TextureAtlas) Minecraft.getInstance().getTextureManager()
                .getTexture(Identifier.fromNamespaceAndPath("minecraft", "textures/atlas/blocks.png")))
                .maxMipLevel;

        glSamplerParameteri(this.blockSampler, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
        glSamplerParameteri(this.blockSampler, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glSamplerParameteri(this.blockSampler, GL_TEXTURE_MIN_LOD, 0);
        glSamplerParameteri(this.blockSampler, GL_TEXTURE_MAX_LOD, mipLvl);//Integer.numberOfTrailingZeros(ModelFactory.MODEL_TEXTURE_SIZE)
    }


    public void free() {
        this.modelBuffer.free();
        this.modelColourBuffer.free();
        this.textures.free();
        glDeleteSamplers(this.blockSampler);
    }


    public void bind(int modelBindingIndex, int colourBindingIndex, int textureBindingIndex) {
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, modelBindingIndex, this.modelBuffer.id());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, colourBindingIndex, this.modelColourBuffer.id());
        bindTextureUnit(textureBindingIndex, this.textures.id());
        glBindSampler(textureBindingIndex, this.blockSampler);
    }
}
