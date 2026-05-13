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
    /**
     * Cross-backend sampler for {@link #textures}. Used by Metal's render
     * encoder path (MDIC's renderTerrainMetal). On GL we keep the legacy
     * {@link #blockSampler} that {@code glBindSampler}-binds directly.
     * Both samplers use the same filter/wrap state so visual output stays
     * consistent across backends.
     */
    public final me.cortex.voxy.client.core.gpu.IGpuSampler atlasSampler;

    public ModelStore() {
        this.modelBuffer = RenderBackendFactory.get().createBuffer(MODEL_SIZE * (1<<16));
        this.modelColourBuffer = RenderBackendFactory.get().createBuffer(4 * (1<<16));
        // M13 chunk 1: allocate the model atlas as CPU-uploadable. On Metal
        // this is Shared storage so `uploadSubImage2D` can push the bakery
        // results into it; on GL the call is identical to `store`. Default
        // sampler/sampling state stays GL-side.
        this.textures = RenderBackendFactory.get().createTexture()
                .storeUploadable(GL_RGBA8,
                        Integer.numberOfTrailingZeros(ModelFactory.MODEL_TEXTURE_SIZE),
                        ModelFactory.MODEL_TEXTURE_SIZE*3*256,
                        ModelFactory.MODEL_TEXTURE_SIZE*2*256)
                .name("ModelTextures");


        //Limit the mips of the texture to match that of the terrain atlas
        int mipLvl = ((TextureAtlas) Minecraft.getInstance().getTextureManager()
                .getTexture(Identifier.fromNamespaceAndPath("minecraft", "textures/atlas/blocks.png")))
                .maxMipLevel;

        glSamplerParameteri(this.blockSampler, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
        glSamplerParameteri(this.blockSampler, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glSamplerParameteri(this.blockSampler, GL_TEXTURE_MIN_LOD, 0);
        glSamplerParameteri(this.blockSampler, GL_TEXTURE_MAX_LOD, mipLvl);//Integer.numberOfTrailingZeros(ModelFactory.MODEL_TEXTURE_SIZE)

        // Cross-backend mirror of blockSampler — same filter/wrap state.
        // Used by Metal's RenderEncoder.setSampler path; GL still uses
        // glBindSampler(unit, this.blockSampler) for its raw-GL draws.
        this.atlasSampler = RenderBackendFactory.get().createSampler(
                me.cortex.voxy.client.core.gpu.SamplerDesc.builder()
                        .filter(me.cortex.voxy.client.core.gpu.SamplerDesc.Filter.NEAREST,
                                me.cortex.voxy.client.core.gpu.SamplerDesc.Filter.NEAREST)
                        .mipFilter(me.cortex.voxy.client.core.gpu.SamplerDesc.MipFilter.LINEAR)
                        .wrap(me.cortex.voxy.client.core.gpu.SamplerDesc.Wrap.CLAMP_TO_EDGE,
                                me.cortex.voxy.client.core.gpu.SamplerDesc.Wrap.CLAMP_TO_EDGE)
                        .lod(0, mipLvl)
                        .label("ModelAtlasSampler")
                        .build());
    }


    public void free() {
        this.modelBuffer.free();
        this.modelColourBuffer.free();
        this.textures.free();
        this.atlasSampler.close();
        glDeleteSamplers(this.blockSampler);
    }


    public void bind(int modelBindingIndex, int colourBindingIndex, int textureBindingIndex) {
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, modelBindingIndex, this.modelBuffer.id());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, colourBindingIndex, this.modelColourBuffer.id());
        bindTextureUnit(textureBindingIndex, this.textures.id());
        glBindSampler(textureBindingIndex, this.blockSampler);
    }

    /**
     * Encoder-aware overload — binds model + colour SSBOs and (M13 chunk 1
     * onward) the model atlas texture + cross-backend sampler. Used by
     * Metal's MDIC render path.
     */
    public void bindBuffers(me.cortex.voxy.client.core.gpu.RenderEncoder encoder,
                            int modelBindingIndex, int colourBindingIndex,
                            int atlasBindingIndex) {
        encoder.setBuffer(modelBindingIndex, this.modelBuffer, 0);
        encoder.setBuffer(colourBindingIndex, this.modelColourBuffer, 0);
        encoder.setTexture(atlasBindingIndex, this.textures);
        encoder.setSampler(atlasBindingIndex, this.atlasSampler);
    }
}
