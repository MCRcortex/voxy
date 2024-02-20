package me.cortex.voxy.client.core.model;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import me.cortex.voxy.client.core.IGetVoxelCore;
import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.GlTexture;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.block.BlockColorProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.LightType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.ColorResolver;
import net.minecraft.world.chunk.light.LightingProvider;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.util.*;
import java.util.stream.Stream;

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

    //TODO: replace the fluid BlockState with a client model id integer of the fluidState, requires looking up
    // the fluid state in the mipper
    private record ModelEntry(List<ColourDepthTextureData> textures, int fluidBlockStateId){
        private ModelEntry(ColourDepthTextureData[] textures, int fluidBlockStateId) {
            this(Stream.of(textures).map(ColourDepthTextureData::clone).toList(), fluidBlockStateId);
        }
    }

    public static final int MODEL_SIZE = 64;
    public final ModelTextureBakery bakery;
    private final GlBuffer modelBuffer;
    private final GlBuffer modelColourBuffer;
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
    private final int[] fluidStateLUT;

    //Provides a map from id -> model id as multiple ids might have the same internal model id
    private final int[] idMappings;
    private final Object2IntOpenHashMap<ModelEntry> modelTexture2id = new Object2IntOpenHashMap<>();


    private final List<Biome> biomes = new ArrayList<>();
    private final List<Pair<Integer, BlockState>> modelsRequiringBiomeColours = new ArrayList<>();

    private static final ObjectSet<BlockState> LOGGED_SELF_CULLING_WARNING = new ObjectOpenHashSet<>();

    public ModelManager(int modelTextureSize) {
        this.modelTextureSize = modelTextureSize;
        this.bakery = new ModelTextureBakery(modelTextureSize, modelTextureSize);
        this.modelBuffer = new GlBuffer(MODEL_SIZE * (1<<16));

        this.modelColourBuffer = new GlBuffer(4 * (1<<16));

        //TODO: figure out how to do mipping :blobfox_pineapple:
        this.textures = new GlTexture().store(GL_RGBA8, 4, modelTextureSize*3*256,modelTextureSize*2*256);
        this.metadataCache = new long[1<<16];
        this.fluidStateLUT = new int[1<<16];
        this.idMappings = new int[1<<20];//Max of 1 million blockstates mapping to 65k model states
        Arrays.fill(this.idMappings, -1);
        Arrays.fill(this.fluidStateLUT, -1);


        glSamplerParameteri(this.blockSampler, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
        glSamplerParameteri(this.blockSampler, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glSamplerParameteri(this.blockSampler, GL_TEXTURE_MIN_LOD, 0);
        glSamplerParameteri(this.blockSampler, GL_TEXTURE_MAX_LOD, 4);

        this.modelTexture2id.defaultReturnValue(-1);
    }




    //TODO: what i need to do is seperate out fluid states from blockStates


    //TODO: so need a few things, per face sizes and offsets, the sizes should be computed from the pixels and find the minimum bounding pixel
    // while the depth is computed from the depth buffer data
    public int addEntry(int blockId, BlockState blockState) {
        if (this.idMappings[blockId] != -1) {
            System.err.println("Block id already added: " + blockId + " for state: " + blockState);
            return this.idMappings[blockId];
        }

        boolean isFluid = blockState.getBlock() instanceof FluidBlock;
        int modelId = -1;
        var textureData = this.bakery.renderFaces(blockState, 123456, isFluid);

        int clientFluidStateId = -1;

        if ((!isFluid) && (!blockState.getFluidState().isEmpty())) {
            //Insert into the fluid LUT
            var fluidState = blockState.getFluidState().getBlockState();

            //TODO:FIXME: PASS IN THE Mapper instead of grabbing it!!! THIS IS CRTICIAL TO FIX
            int fluidStateId = ((IGetVoxelCore)MinecraftClient.getInstance().worldRenderer).getVoxelCore().getWorldEngine().getMapper().getIdForBlockState(fluidState);


            clientFluidStateId = this.idMappings[fluidStateId];
            if (clientFluidStateId == -1) {
                clientFluidStateId = this.addEntry(fluidStateId, fluidState);
            }
        }

        {//Deduplicate same entries
            var entry = new ModelEntry(textureData, clientFluidStateId);
            int possibleDuplicate = this.modelTexture2id.getInt(entry);
            if (possibleDuplicate != -1) {//Duplicate found
                this.idMappings[blockId] = possibleDuplicate;
                modelId = possibleDuplicate;
                return possibleDuplicate;
            } else {//Not a duplicate so create a new entry
                modelId = this.modelTexture2id.size();
                this.idMappings[blockId] = modelId;
                this.modelTexture2id.put(entry, modelId);
            }
        }

        if (isFluid) {
            this.fluidStateLUT[modelId] = modelId;
        } else if (clientFluidStateId != -1) {
            this.fluidStateLUT[modelId] = clientFluidStateId;
        }

        var colourProvider = MinecraftClient.getInstance().getBlockColors().providers.get(Registries.BLOCK.getRawId(blockState.getBlock()));


        RenderLayer blockRenderLayer = null;
        if (blockState.getBlock() instanceof FluidBlock) {
            blockRenderLayer = RenderLayers.getFluidLayer(blockState.getFluidState());
        } else {
            blockRenderLayer = RenderLayers.getBlockLayer(blockState);
        }


        int checkMode = blockRenderLayer==RenderLayer.getSolid()?TextureUtils.WRITE_CHECK_STENCIL:TextureUtils.WRITE_CHECK_ALPHA;




        long uploadPtr = UploadStream.INSTANCE.upload(this.modelBuffer, (long) modelId * MODEL_SIZE, MODEL_SIZE);


        //TODO: implement;
        // TODO: if it has a constant colour instead... idk why (apparently for things like spruce leaves)?? but premultiply the texture data by the constant colour
        boolean hasBiomeColourResolver = false;
        if (colourProvider != null) {
            hasBiomeColourResolver = isBiomeDependentColour(colourProvider, blockState);
        }



        //TODO: special case stuff like vines and glow lichen, where it can be represented by a single double sided quad
        // since that would help alot with perf of lots of vines, can be done by having one of the faces just not exist and the other be in no occlusion mode

        var sizes = this.computeModelDepth(textureData, checkMode);

        //TODO: THIS, note this can be tested for in 2 ways, re render the model with quad culling disabled and see if the result
        // is the same, (if yes then needs double sided quads)
        // another way to test it is if e.g. up and down havent got anything rendered but the sides do (e.g. all plants etc)
        boolean needsDoubleSidedQuads = (sizes[0] < -0.1 && sizes[1] < -0.1) || (sizes[2] < -0.1 && sizes[3] < -0.1) || (sizes[4] < -0.1 && sizes[5] < -0.1);


        boolean cullsSame = false;

        {
            //TODO: Could also move this into the RenderDataFactory and do it on the actual blockstates instead of a guestimation
            boolean allTrue = true;
            boolean allFalse = true;
            //Guestimation test for if the block culls itself
            for (var dir : Direction.values()) {
                if (blockState.isSideInvisible(blockState, dir)) {
                    allFalse = false;
                } else {
                    allTrue = false;
                }
            }

            if (allFalse == allTrue) {//If only some sides where self culled then abort
                cullsSame = false;
                if (LOGGED_SELF_CULLING_WARNING.add(blockState)) System.err.println("Warning! blockstate: " + blockState + " only culled against its self some of the time");
            }

            if (allTrue) {
                cullsSame = true;
            }
        }


        //Each face gets 1 byte, with the top 2 bytes being for whatever
        long metadata = 0;
        metadata |= hasBiomeColourResolver?1:0;
        metadata |= blockRenderLayer == RenderLayer.getTranslucent()?2:0;
        metadata |= needsDoubleSidedQuads?4:0;
        metadata |= (!blockState.getFluidState().isEmpty())?8:0;//Has a fluid state accosiacted with it
        metadata |= isFluid?16:0;//Is a fluid

        metadata |= cullsSame?32:0;

        //TODO: add a bunch of control config options for overriding/setting options of metadata for each face of each type
        for (int face = 5; face != -1; face--) {//In reverse order to make indexing into the metadata long easier
            long faceUploadPtr = uploadPtr + 4L * face;//Each face gets 4 bytes worth of data
            metadata <<= 8;
            float offset = sizes[face];
            if (offset < -0.1) {//Face is empty, so ignore
                metadata |= 0xFF;//Mark the face as non-existent
                //Set to -1 as safepoint
                MemoryUtil.memPutInt(faceUploadPtr, -1);
                continue;
            }
            var faceSize = TextureUtils.computeBounds(textureData[face], checkMode);
            int writeCount = TextureUtils.getWrittenPixelCount(textureData[face], checkMode);

            boolean faceCoversFullBlock = faceSize[0] == 0 && faceSize[2] == 0 &&
                    faceSize[1] == (this.modelTextureSize-1) && faceSize[3] == (this.modelTextureSize-1);

            metadata |= faceCoversFullBlock?2:0;

            //TODO: add alot of config options for the following
            boolean occludesFace = true;
            occludesFace &= blockRenderLayer != RenderLayer.getTranslucent();//If its translucent, it doesnt occlude

            //TODO: make this an option, basicly if the face is really close, it occludes otherwise it doesnt
            occludesFace &= offset < 0.1;//If the face is rendered far away from the other face, then it doesnt occlude

            if (occludesFace) {
                occludesFace &= ((float)writeCount)/(this.modelTextureSize * this.modelTextureSize) > 0.9;// only occlude if the face covers more than 90% of the face
            }
            metadata |= occludesFace?1:0;



            boolean canBeOccluded = true;
            //TODO: make this an option on how far/close
            canBeOccluded &= offset < 0.3;//If the face is rendered far away from the other face, then it cant be occluded

            metadata |= canBeOccluded?4:0;

            //Face uses its own lighting if its not flat against the adjacent block & isnt traslucent
            metadata |= (offset != 0 || blockRenderLayer == RenderLayer.getTranslucent())?0b1000:0;



            //Scale face size from 0->this.modelTextureSize-1 to 0->15
            for (int i = 0; i < 4; i++) {
                faceSize[i] = Math.round((((float)faceSize[i])/(this.modelTextureSize-1))*15);
            }

            int faceModelData = 0;
            faceModelData |= faceSize[0] | (faceSize[1]<<4) | (faceSize[2]<<8) | (faceSize[3]<<12);
            faceModelData |= Math.round(offset*63)<<16;//Change the scale from 0->1 (ends inclusive) float to 0->63 (6 bits) NOTE! that 63 == 1.0f meaning its shifted all the way to the other side of the model
            //Still have 11 bits free

            //Stuff like fences are solid, however they have extra side piece that mean it needs to have discard on
            int area = (faceSize[1]-faceSize[0]+1) * (faceSize[3]-faceSize[2]+1);
            boolean needsAlphaDiscard = ((float)writeCount)/area<0.9;//If the amount of area covered by written pixels is less than a threashold, disable discard as its not needed

            needsAlphaDiscard |= blockRenderLayer != RenderLayer.getSolid();
            needsAlphaDiscard &= blockRenderLayer != RenderLayer.getTranslucent();//Translucent doesnt have alpha discard
            faceModelData |= needsAlphaDiscard?1<<22:0;

            faceModelData |= ((!faceCoversFullBlock)&&blockRenderLayer != RenderLayer.getTranslucent())?1<<23:0;//Alpha discard override, translucency doesnt have alpha discard



            MemoryUtil.memPutInt(faceUploadPtr, faceModelData);
        }
        this.metadataCache[modelId] = metadata;

        uploadPtr += 4*6;
        //Have 40 bytes free for remaining model data
        // todo: put in like the render layer type ig? along with colour resolver info
        int modelFlags = 0;
        modelFlags |= colourProvider != null?1:0;
        modelFlags |= hasBiomeColourResolver?2:0;//Basicly whether to use the next int as a colour or as a base index/id into a colour buffer for biome dependent colours
        modelFlags |= blockRenderLayer == RenderLayer.getTranslucent()?4:0;
        modelFlags |= blockRenderLayer == RenderLayer.getCutout()?0:8;

        //modelFlags |= blockRenderLayer == RenderLayer.getSolid()?0:1;// should discard alpha
        MemoryUtil.memPutInt(uploadPtr, modelFlags);
        //Temporary override to always be non biome specific
        if (colourProvider == null) {
            MemoryUtil.memPutInt(uploadPtr + 4, -1);//Set the default to nothing so that its faster on the gpu
        } else if (!hasBiomeColourResolver) {
            Biome defaultBiome = MinecraftClient.getInstance().world.getRegistryManager().get(RegistryKeys.BIOME).get(BiomeKeys.PLAINS);
            MemoryUtil.memPutInt(uploadPtr + 4, captureColourConstant(colourProvider, blockState, defaultBiome)|0xFF000000);
        } else if (!this.biomes.isEmpty()) {
            //Populate the list of biomes for the model state
            int biomeIndex = this.modelsRequiringBiomeColours.size() * this.biomes.size();
            MemoryUtil.memPutInt(uploadPtr + 4, biomeIndex);
            this.modelsRequiringBiomeColours.add(new Pair<>(modelId, blockState));
            long clrUploadPtr = UploadStream.INSTANCE.upload(this.modelColourBuffer, biomeIndex * 4L, 4L * this.biomes.size());
            for (var biome : this.biomes) {
                MemoryUtil.memPutInt(clrUploadPtr, captureColourConstant(colourProvider, blockState, biome)|0xFF000000); clrUploadPtr += 4;
            }
        }


        //Note: if the layer isSolid then need to fill all the points in the texture where alpha == 0 with the average colour
        // of the surrounding blocks but only within the computed face size bounds
        //TODO


        this.putTextures(modelId, textureData);

        //glGenerateTextureMipmap(this.textures.id);
        return modelId;
    }

    public void addBiome(int id, Biome biome) {
        this.biomes.add(biome);
        if (this.biomes.size()-1 != id) {
            throw new IllegalStateException("Biome ordering not consistent with biome id for biome " + biome + " expected id: " + (this.biomes.size()-1) + " got id: " + id);
        }

        int i = 0;
        for (var entry : this.modelsRequiringBiomeColours) {
            var colourProvider = MinecraftClient.getInstance().getBlockColors().providers.get(Registries.BLOCK.getRawId(entry.getRight().getBlock()));
            if (colourProvider == null) {
                throw new IllegalStateException();
            }
            //Populate the list of biomes for the model state
            int biomeIndex = (i++) * this.biomes.size();
            MemoryUtil.memPutInt( UploadStream.INSTANCE.upload(this.modelBuffer, (entry.getLeft()*MODEL_SIZE)+ 4*6 + 4, 4), biomeIndex);
            long clrUploadPtr = UploadStream.INSTANCE.upload(this.modelColourBuffer, biomeIndex * 4L, 4L * this.biomes.size());
            for (var biomeE : this.biomes) {
                MemoryUtil.memPutInt(clrUploadPtr, captureColourConstant(colourProvider, entry.getRight(), biomeE)|0xFF000000); clrUploadPtr += 4;
            }
        }
    }


    //TODO: add a method to detect biome dependent colours (can do by detecting if getColor is ever called)
    // if it is, need to add it to a list and mark it as biome colour dependent or something then the shader
    // will either use the uint as an index or a direct colour multiplier
    private static int captureColourConstant(BlockColorProvider colorProvider, BlockState state, Biome biome) {
        return colorProvider.getColor(state, new BlockRenderView() {
            @Override
            public float getBrightness(Direction direction, boolean shaded) {
                return 0;
            }

            @Override
            public int getLightLevel(LightType type, BlockPos pos) {
                return 0;
            }

            @Override
            public LightingProvider getLightingProvider() {
                return null;
            }

            @Override
            public int getColor(BlockPos pos, ColorResolver colorResolver) {
                return colorResolver.getColor(biome, 0, 0);
            }

            @Nullable
            @Override
            public BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                return state;
            }

            @Override
            public FluidState getFluidState(BlockPos pos) {
                return state.getFluidState();
            }

            @Override
            public int getHeight() {
                return 0;
            }

            @Override
            public int getBottomY() {
                return 0;
            }
        }, BlockPos.ORIGIN, 0);
    }

    private static boolean isBiomeDependentColour(BlockColorProvider colorProvider, BlockState state) {
        boolean[] biomeDependent = new boolean[1];
        colorProvider.getColor(state, new BlockRenderView() {
            @Override
            public float getBrightness(Direction direction, boolean shaded) {
                return 0;
            }

            @Override
            public int getLightLevel(LightType type, BlockPos pos) {
                return 0;
            }

            @Override
            public LightingProvider getLightingProvider() {
                return null;
            }

            @Override
            public int getColor(BlockPos pos, ColorResolver colorResolver) {
                biomeDependent[0] = true;
                return 0;
            }

            @Nullable
            @Override
            public BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                return state;
            }

            @Override
            public FluidState getFluidState(BlockPos pos) {
                return state.getFluidState();
            }

            @Override
            public int getHeight() {
                return 0;
            }

            @Override
            public int getBottomY() {
                return 0;
            }
        }, BlockPos.ORIGIN, 0);
        return biomeDependent[0];
    }




    public static boolean faceExists(long metadata, int face) {
        return ((metadata>>(8*face))&0xFF)!=0xFF;
    }

    public static boolean faceCanBeOccluded(long metadata, int face) {
        return ((metadata>>(8*face))&0b100)==0b100;
    }

    public static boolean faceOccludes(long metadata, int face) {
        return faceExists(metadata, face) && ((metadata>>(8*face))&0b1)==0b1;
    }

    public static boolean faceUsesSelfLighting(long metadata, int face) {
        return ((metadata>>(8*face))&0b1000) != 0;
    }

    public static boolean isDoubleSided(long metadata) {
        return ((metadata>>(8*6))&4) != 0;
    }

    public static boolean isTranslucent(long metadata) {
        return ((metadata>>(8*6))&2) != 0;
    }

    public static boolean containsFluid(long metadata) {
        return ((metadata>>(8*6))&8) != 0;
    }

    public static boolean isFluid(long metadata) {
        return ((metadata>>(8*6))&16) != 0;
    }

    public static boolean isBiomeColoured(long metadata) {
        return ((metadata>>(8*6))&1) != 0;
    }

    //NOTE: this might need to be moved to per face
    public static boolean cullsSame(long metadata) {
        return ((metadata>>(8*6))&32) != 0;
    }










    private float[] computeModelDepth(ColourDepthTextureData[] textures, int checkMode) {
        float[] res = new float[6];
        for (var dir : Direction.values()) {
            var data = textures[dir.getId()];
            float fd = TextureUtils.computeDepth(data, TextureUtils.DEPTH_MODE_AVG, checkMode);//Compute the min float depth, smaller means closer to the camera, range 0-1
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

    //TODO:FIXME: DONT DO SPIN LOCKS :WAA:
    public long getModelMetadata(int blockId) {
        int map = this.idMappings[blockId];
        if (map == -1) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            map = this.idMappings[blockId];
        }
        if (map == -1) {
            throw new IdNotYetComputedException(blockId);
        }
        return this.metadataCache[map];
        //int map = 0;
        //int i = 10;
        //while ((map = this.idMappings[blockId]) == -1) {
        //    Thread.onSpinWait();
        //}

        //long meta = 0;
        //while ((meta = this.metadataCache[map]) == 0) {
        //    Thread.onSpinWait();
        //}
    }

    public long getModelMetadataFromClientId(int clientId) {
        return this.metadataCache[clientId];
    }

    public int getModelId(int blockId) {
        int map = this.idMappings[blockId];
        if (map == -1) {
            throw new IdNotYetComputedException(blockId);
        }
        return map;
    }

    public int getFluidClientStateId(int clientBlockStateId) {
        int map = this.fluidStateLUT[clientBlockStateId];
        if (map == -1) {
            throw new IdNotYetComputedException(clientBlockStateId);
        }
        return map;
    }

    private void putTextures(int id, ColourDepthTextureData[] textures) {
        int X = (id&0xFF) * this.modelTextureSize*3;
        int Y = ((id>>8)&0xFF) * this.modelTextureSize*2;

        for (int subTex = 0; subTex < 6; subTex++) {
            int x = X + (subTex>>1)*this.modelTextureSize;
            int y = Y + (subTex&1)*this.modelTextureSize;

            GlStateManager._pixelStore(GlConst.GL_UNPACK_ROW_LENGTH, 0);
            GlStateManager._pixelStore(GlConst.GL_UNPACK_SKIP_PIXELS, 0);
            GlStateManager._pixelStore(GlConst.GL_UNPACK_SKIP_ROWS, 0);
            GlStateManager._pixelStore(GlConst.GL_UNPACK_ALIGNMENT, 4);
            var current = textures[subTex].colour();
            var next = new int[current.length>>1];
            for (int i = 0; i < 4; i++) {
                glTextureSubImage2D(this.textures.id, i, x>>i, y>>i, this.modelTextureSize>>i, this.modelTextureSize>>i, GL_RGBA, GL_UNSIGNED_BYTE, current);

                int size = this.modelTextureSize>>(i+1);
                for (int pX = 0; pX < size; pX++) {
                    for (int pY = 0; pY < size; pY++) {
                        int C00 = current[(pY*2)*size+pX*2];
                        int C01 = current[(pY*2+1)*size+pX*2];
                        int C10 = current[(pY*2)*size+pX*2+1];
                        int C11 = current[(pY*2+1)*size+pX*2+1];
                        next[pY*size+pX] = TextureUtils.mipColours(C00, C01, C10, C11);
                    }
                }

                current = next;
                next = new int[current.length>>1];
            }
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

    public int getColourBufferId() {
        return this.modelColourBuffer.id;
    }

    public void free() {
        this.bakery.free();
        this.modelBuffer.free();
        this.modelColourBuffer.free();
        this.textures.free();
        glDeleteSamplers(this.blockSampler);
    }

    public void addDebugInfo(List<String> info) {
        info.add("BlockModels registered: " + this.modelTexture2id.size() + "/" + (1<<16));
    }
}
