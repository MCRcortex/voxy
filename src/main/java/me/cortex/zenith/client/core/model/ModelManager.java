package me.cortex.zenith.client.core.model;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import me.cortex.zenith.client.core.gl.GlBuffer;
import me.cortex.zenith.client.core.gl.GlTexture;
import me.cortex.zenith.client.core.rendering.util.UploadStream;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Direction;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    private final int modelTextureSize;

    //Model data might also contain a constant colour if the colour resolver produces a constant colour, this saves space in the
    // section buffer reverse indexing

    //model data also contains if a face should be randomly rotated,flipped etc to get rid of moire effect
    // this would be done in the fragment shader

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
    private final Object2IntOpenHashMap<List<ColourDepthTextureData>> modelTexture2id = new Object2IntOpenHashMap<>();

    public ModelManager(int modelTextureSize) {
        this.modelTextureSize = modelTextureSize;
        this.bakery = new ModelTextureBakery(modelTextureSize, modelTextureSize);
        this.modelBuffer = new GlBuffer(MODEL_SIZE * (1<<16));
        //TODO: figure out how to do mipping :blobfox_pineapple:
        this.textures = new GlTexture().store(GL_RGBA8, 1, modelTextureSize*3*256,modelTextureSize*2*256);
        this.metadataCache = new long[1<<16];
        this.idMappings = new int[1<<16];
        Arrays.fill(this.idMappings, -1);


        glSamplerParameteri(this.blockSampler, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
        glSamplerParameteri(this.blockSampler, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glSamplerParameteri(this.blockSampler, GL_TEXTURE_MIN_LOD, 0);
        glSamplerParameteri(this.blockSampler, GL_TEXTURE_MAX_LOD, 4);

        this.modelTexture2id.defaultReturnValue(-1);
    }


    //TODO: so need a few things, per face sizes and offsets, the sizes should be computed from the pixels and find the minimum bounding pixel
    // while the depth is computed from the depth buffer data
    public int addEntry(int blockId, BlockState blockState) {
        if (this.idMappings[blockId] != -1) {
            throw new IllegalArgumentException("Trying to add entry for duplicate id");
        }

        int modelId = -1;
        var textureData = this.bakery.renderFaces(blockState, 123456);
        {//Deduplicate same entries
            int possibleDuplicate = this.modelTexture2id.getInt(List.of(textureData));
            if (possibleDuplicate != -1) {//Duplicate found
                this.idMappings[blockId] = possibleDuplicate;
                modelId = possibleDuplicate;
                return possibleDuplicate;
            } else {//Not a duplicate so create a new entry
                modelId = this.modelTexture2id.size();
                this.idMappings[blockId] = modelId;
                this.modelTexture2id.put(List.of(textureData), modelId);
            }
        }
        this.putTextures(modelId, textureData);

        var colourProvider = MinecraftClient.getInstance().getBlockColors().providers.get(Registries.BLOCK.getRawId(blockState.getBlock()));

        var blockRenderLayer = RenderLayers.getBlockLayer(blockState);
        int checkMode = blockRenderLayer==RenderLayer.getSolid()?TextureUtils.WRITE_CHECK_STENCIL:TextureUtils.WRITE_CHECK_ALPHA;

        //If it is the solid layer, it is _always_ going to occlude fully for all written pixels, even if they are 100% translucent, this should save alot of resources
        // if it is cutout it might occlude might not, need to test
        // if it is translucent it will _never_ occlude

        //NOTE: this is excluding fluid states




        //Model data contains, the quad size and offset of each face and whether the face needs to be resolved with a colour modifier
        // sourced from the quad data and reverse indexed into the section data (meaning there will be a maxiumum number of colours)
        // can possibly also put texture coords if needed
        //Supplying the quad size and offset means that much more accurate rendering quads are rendered and stuff like snow layers will look correct
        // the size/offset of the corners of the quads will only be applied to the corner quads of the merged quads with adjustment to the UV to ensure textures are not alignned weirdly
        // the other axis offset is always applied and means that the models will look more correct even when merged into a large quad (which will have alot of overdraw)
        //TODO: need to make an option for like leaves to be fully opaque as by default they are not!!!!










        //Models data is computed per face, the axis offset of the face is computed from the depth component of the rasterized data
        // the size and offset of the face data is computed from the remaining pixels that where actually rastered (e.g. depth != 1.0)
        //  (note this might be able to be optimized for cuttout layers where it automatically tries to squish as much as possible)
        // solid layer renders it as black so might need to add a bitset in the model data of whether the face is rendering
        // in discard or solid mode maybe?

        long uploadPtr = UploadStream.INSTANCE.upload(this.modelBuffer, (long) modelId * MODEL_SIZE, MODEL_SIZE);


        //TODO: implement
        boolean hasBiomeColourResolver = false;


        //TODO: THIS, note this can be tested for in 2 ways, re render the model with quad culling disabled and see if the result
        // is the same, (if yes then needs double sided quads)
        // another way to test it is if e.g. up and down havent got anything rendered but the sides do (e.g. all plants etc)
        boolean needsDoubleSidedQuads = false;

        //TODO: special case stuff like vines and glow lichen, where it can be represented by a single double sided quad
        // since that would help alot with perf of lots of vines


        //This also checks if there is a block colour resolver for the given blockstate and marks that the block has a resolver
        var sizes = this.computeModelDepth(textureData, checkMode);

        //Each face gets 1 byte, with the top 2 bytes being for whatever
        long metadata = hasBiomeColourResolver?1:0;
        metadata |= blockRenderLayer == RenderLayer.getTranslucent()?2:0;

        //TODO: add a bunch of control config options for overriding/setting options of metadata for each face of each type
        for (int face = 5; face != -1; face--) {//In reverse order to make indexing into the metadata long easier
            long faceUploadPtr = uploadPtr + 4L * face;//Each face gets 4 bytes worth of data
            metadata <<= 8;
            float offset = sizes[face];
            if (offset < -0.1) {//Face is empty, so ignore
                metadata |= 0xFF;//Mark the face as non-existent
                //Set to -1 as safepoint
                MemoryUtil.memPutLong(faceUploadPtr, -1);
                continue;
            }
            var faceSize = TextureUtils.computeBounds(textureData[face], checkMode);

            boolean faceCoversFullBlock = faceSize[0] == 0 && faceSize[2] == 0 &&
                    faceSize[1] == (this.modelTextureSize-1) && faceSize[3] == (this.modelTextureSize-1);

            metadata |= faceCoversFullBlock?2:0;

            //TODO: add alot of config options for the following
            boolean occludesFace = true;
            occludesFace &= blockRenderLayer != RenderLayer.getTranslucent();//If its translucent, it doesnt occlude

            //TODO: make this an option, basicly if the face is really close, it occludes otherwise it doesnt
            occludesFace &= offset < 0.1;//If the face is rendered far away from the other face, then it doesnt occlude

            if (occludesFace) {
                int writeCount = TextureUtils.getWrittenPixelCount(textureData[face], checkMode);
                occludesFace &= ((float)writeCount)/(this.modelTextureSize * this.modelTextureSize) > 0.9;// only occlude if the face covers more than 90% of the face
            }
            metadata |= occludesFace?1:0;


            //Scale face size from 0->this.modelTextureSize-1 to 0->15
            for (int i = 0; i < 4; i++) {
                faceSize[i] = Math.round((((float)faceSize[i])/(this.modelTextureSize-1))*15);
            }

            int faceModelData = 0;
            faceModelData |= faceSize[0] | (faceSize[1]<<4) | (faceSize[2]<<8) | (faceSize[3]<<12);
            faceModelData |= Math.round(offset*63)<<16;//Change the scale from 0->1 (ends inclusive) float to 0->63 (6 bits) NOTE! that 63 == 1.0f meaning its shifted all the way to the other side of the model
            //Still have 11 bits free

            MemoryUtil.memPutInt(faceUploadPtr, 0);
        }
        this.metadataCache[modelId] = metadata;

        uploadPtr += 4*6;
        //Have 40 bytes free for remaining model data
        // todo: put in like the render layer type ig? along with colour resolver info



        return modelId;
    }














    private float[] computeModelDepth(ColourDepthTextureData[] textures, int checkMode) {
        float[] res = new float[6];
        for (var dir : Direction.values()) {
            var data = textures[dir.getId()];
            float fd = TextureUtils.computeDepth(data, TextureUtils.DEPTH_MODE_MIN, checkMode);//Compute the min float depth, smaller means closer to the camera, range 0-1
            int depth = Math.round(fd * this.modelTextureSize);
            //If fd is -1, it means that there was nothing rendered on that face and it should be discarded
            if (fd < -0.1) {
                res[dir.ordinal()] = -1;
            } else {
                res[dir.ordinal()] = ((float) depth)/this.modelTextureSize;
            }
        }
        return res;
    }

    public long getModelMetadata(int blockId) {
        int map = this.idMappings[blockId];
        if (map == -1) {
            throw new IllegalArgumentException("Id hasnt been computed yet");
        }
        return this.metadataCache[map];
    }

    public int getModelId(int blockId) {
        int map = this.idMappings[blockId];
        if (map == -1) {
            throw new IllegalArgumentException("Id hasnt been computed yet");
        }
        return map;
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
