package me.cortex.zenith.client.core.model;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;
import me.cortex.zenith.client.core.gl.GlBuffer;
import me.cortex.zenith.client.core.gl.GlTexture;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.registry.Registries;
import org.lwjgl.opengl.GL45C;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_NEAREST_MIPMAP_LINEAR;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_MAX_LOD;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_MIN_LOD;
import static org.lwjgl.opengl.GL33.glDeleteSamplers;
import static org.lwjgl.opengl.GL33.glGenSamplers;
import static org.lwjgl.opengl.GL33C.glSamplerParameteri;
import static org.lwjgl.opengl.GL45C.glTextureSubImage2D;

//Manages the storage and updating of model states, textures and colours

//Also has a fast long[] based metadata lookup for when the terrain mesher needs to look up the face occlusion data

//TODO: support more than 65535 states, what should actually happen is a blockstate is registered, the model data is generated, then compared
// to all other models already loaded, if it is a duplicate, create a mapping from the id to the already loaded id, this will help with meshing aswell
// as leaves and such will be able to be merged
public class ModelManager {
    public static final int MODEL_SIZE = 64;
    private final ModelTextureBakery bakery;
    private final GlBuffer modelBuffer;
    private final GlTexture textures;
    private final int blockSampler = glGenSamplers();

    //Model data might also contain a constant colour if the colour resolver produces a constant colour, this saves space in the
    // section buffer reverse indexing

    //The Meta-cache contains critical information needed for meshing, colour provider bit, per-face = is empty, has alpha, is solid, full width, full height
    // alpha means that some pixels have alpha values and belong in the translucent rendering layer,
    // is empty means that the face is air/shouldent be rendered as there is nothing there
    // is solid means that every pixel is fully opaque
    // full width, height, is if the blockmodel dimentions occupy a full block, e.g. comparator, some faces do some dont and some only in a specific axis

    //FIXME: the issue is e.g. leaves are translucent but the alpha value is used to colour the leaves, so a block can have alpha but still be only made up of transparent or opaque pixels
    // will need to find a way to send this info to the shader via the material, if it is in the opaque phase render as transparent with blending shiz

    //TODO: ADD an occlusion mask that can be queried (16x16 pixels takes up 4 longs) this mask shows what pixels are exactly occluded at the edge of the block
    // so that full block occlusion can work nicely


    //TODO: what might work maybe, is that all the transparent pixels should be set to the average of the other pixels
    // that way the block is always "fully occluding" (if the block model doesnt cover the entire thing), maybe
    // this has some issues with quad merging
    //TODO: ACTUALLY, full out all the transparent pixels that are _within_ the bounding box of the model
    // this will mean that when quad merging and rendering, the transparent pixels of the block where there shouldent be
    // might still work???

    // this has an issue with scaffolding i believe tho, so maybe make it a probability to render??? idk
    private final long[] metadataCache;

    //Provides a map from id -> model id as multiple ids might have the same internal model id
    private final int[] idMappings;
    private final int modelTextureSize;

    public ModelManager(int modelTextureSize) {
        this.modelTextureSize = modelTextureSize;
        this.bakery = new ModelTextureBakery(modelTextureSize, modelTextureSize);
        this.modelBuffer = new GlBuffer(MODEL_SIZE * (1<<16));
        //TODO: figure out how to do mipping :blobfox_pineapple:
        this.textures = new GlTexture().store(GL_RGBA8, 1, modelTextureSize*3*256,modelTextureSize*2*256);
        this.metadataCache = new long[1<<16];
        this.idMappings = new int[1<<16];


        glSamplerParameteri(this.blockSampler, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
        glSamplerParameteri(this.blockSampler, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glSamplerParameteri(this.blockSampler, GL_TEXTURE_MIN_LOD, 0);
        glSamplerParameteri(this.blockSampler, GL_TEXTURE_MAX_LOD, 4);
    }

    public void updateEntry(int id, BlockState blockState) {
        var colourProvider = MinecraftClient.getInstance().getBlockColors().providers.get(Registries.BLOCK.getRawId(blockState.getBlock()));

        //This also checks if there is a block colour resolver for the given blockstate and marks that the block has a resolver
        var textureData = this.bakery.renderFaces(blockState, 123456);
        int depth = TextureUtils.computeDepth(textureData[0], TextureUtils.DEPTH_MODE_AVG);

        int aaaa = 1;

        this.putTextures(id, textureData);


        //Model data contains, the quad size and offset of each face and whether the face needs to be resolved with a colour modifier
        // sourced from the quad data and reverse indexed into the section data (meaning there will be a maxiumum number of colours)
        // can possibly also put texture coords if needed
        //Supplying the quad size and offset means that much more accurate rendering quads are rendered and stuff like snow layers will look correct
        // the size/offset of the corners of the quads will only be applied to the corner quads of the merged quads with adjustment to the UV to ensure textures are not alignned weirdly
        // the other axis offset is always applied and means that the models will look more correct even when merged into a large quad (which will have alot of overdraw)
        //TODO: need to make an option for like leaves to be fully opaque as by default they are not!!!!


    }

    private void putTextures(int id, ColourDepthTextureData[] textures) {
        int X = (id&0xFF) * this.modelTextureSize*3;
        int Y = ((id>>8)&0xFF) * this.modelTextureSize*2;
        for (int subTex = 0; subTex < 6; subTex++) {
            int x = X + (subTex%3)*this.modelTextureSize;
            int y = Y + (subTex/3)*this.modelTextureSize;

            GlStateManager._pixelStore(GlConst.GL_UNPACK_ROW_LENGTH, 0);
            GlStateManager._pixelStore(GlConst.GL_UNPACK_SKIP_PIXELS, 0);
            GlStateManager._pixelStore(GlConst.GL_UNPACK_SKIP_ROWS, 0);
            GlStateManager._pixelStore(GlConst.GL_UNPACK_ALIGNMENT, 4);
            glTextureSubImage2D(this.textures.id, 0, x, y, this.modelTextureSize, this.modelTextureSize, GL_RGBA, GL_UNSIGNED_BYTE, textures[subTex].colour());
        }
    }

    public long getModelMetadata(int id) {
        return this.metadataCache[id];
    }

    public int getBufferId() {
        return this.modelBuffer.id;
    }

    public int getTextureId() {
        return this.textures.id;
    }
    public int getSamplerId() {
        return this.blockSampler;
    }

    public void free() {
        this.bakery.free();
        this.modelBuffer.free();
        this.textures.free();
        glDeleteSamplers(this.blockSampler);
    }
}
