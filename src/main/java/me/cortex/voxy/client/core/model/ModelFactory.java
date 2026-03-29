package me.cortex.voxy.client.core.model;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.GlTexture;
import me.cortex.voxy.client.core.model.bakery.SoftwareModelTextureBakery;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.util.Pair;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.ReentrantLock;

import static me.cortex.voxy.client.core.model.ModelStore.MODEL_SIZE;
import static org.lwjgl.opengl.ARBDirectStateAccess.nglTextureSubImage2D;
import static org.lwjgl.opengl.GL11.*;

//Manages the storage and updating of model states, textures and colours

//Also has a fast long[] based metadata lookup for when the terrain mesher needs to look up the face occlusion data

//TODO: support more than 65535 states, what should actually happen is a blockstate is registered, the model data is generated, then compared
// to all other models already loaded, if it is a duplicate, create a mapping from the id to the already loaded id, this will help with meshing aswell
// as leaves and such will be able to be merged



//TODO: NOTE!!! is it worth even uploading as a 16x16 texture, since automatic lod selection... doing 8x8 textures might be perfectly ok!!!
// this _quarters_ the memory requirements for the texture atlas!!! WHICH IS HUGE saving
public class ModelFactory {
    public static final int MODEL_TEXTURE_SIZE = 16;
    public static final int LAYERS = Integer.numberOfTrailingZeros(MODEL_TEXTURE_SIZE);

    //TODO: replace the fluid BlockState with a client model id integer of the fluidState, requires looking up
    // the fluid state in the mipper
    private record ModelEntry(ColourDepthTextureData down, ColourDepthTextureData up, ColourDepthTextureData north, ColourDepthTextureData south, ColourDepthTextureData west, ColourDepthTextureData east, int fluidBlockStateId, int tintingColour) {
        public ModelEntry(ColourDepthTextureData[] textures, int fluidBlockStateId, int tintingColour) {
            this(textures[0], textures[1], textures[2], textures[3], textures[4], textures[5], fluidBlockStateId, tintingColour);
        }
    }

    private final Biome DEFAULT_BIOME = Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS).value();

    public final SoftwareModelTextureBakery bakery2;
    private final long bakeScratchBuffer = MemoryUtil.nmemAlloc(MODEL_TEXTURE_SIZE*MODEL_TEXTURE_SIZE*8*6);


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

    //Contains the set of all block ids that are currently inflight/being baked
    // this is required due to "async" nature of gpu feedback
    private final IntOpenHashSet blockStatesInFlight = new IntOpenHashSet();
    private final ReentrantLock blockStatesInFlightLock = new ReentrantLock();

    private final List<Biome> biomes = new ArrayList<>();
    private final List<Pair<Integer, BlockState>> modelsRequiringBiomeColours = new ArrayList<>();

    private static final ObjectSet<BlockState> LOGGED_SELF_CULLING_WARNING = new ObjectOpenHashSet<>();

    private final Mapper mapper;
    private final ModelStore storage;

    private final ConcurrentLinkedDeque<BlockBake> bakeQueue = new ConcurrentLinkedDeque<>();

    private final ConcurrentLinkedDeque<ResultUploader> uploadResults = new ConcurrentLinkedDeque<>();

    private Object2IntMap<BlockState> customBlockStateIdMapping;

    private static final Field STAIR_BASE_STATE_FIELD;
    static {
        Field f = null;
        try {
            for (Field field : StairBlock.class.getDeclaredFields()) {
                if (field.getType() == BlockState.class) {
                    field.setAccessible(true);
                    f = field;
                    break;
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        STAIR_BASE_STATE_FIELD = f;
    }

    //TODO: NOTE!!! is it worth even uploading as a 16x16 texture, since automatic lod selection... doing 8x8 textures might be perfectly ok!!!
    // this _quarters_ the memory requirements for the texture atlas!!! WHICH IS HUGE saving
    public ModelFactory(Mapper mapper, ModelStore storage) {
        this.mapper = mapper;
        this.storage = storage;
        this.bakery2 = new SoftwareModelTextureBakery();
        this.bakery2.setupTexture();

        this.metadataCache = new long[1<<16];
        this.fluidStateLUT = new int[1<<16];
        this.idMappings = new int[1<<20];//Max of 1 million blockstates mapping to 65k model states
        Arrays.fill(this.idMappings, -1);
        Arrays.fill(this.fluidStateLUT, -1);

        this.modelTexture2id.defaultReturnValue(-1);
        this.addEntry(0);//Add air as the first entry
    }

    public void setCustomBlockStateMapping(Object2IntMap<BlockState> mapping) {
        this.customBlockStateIdMapping = mapping;
    }

    private static final record BlockBake(int blockId, BlockState state) {
    }

    public boolean addEntry(int blockId) {
        if (this.idMappings[blockId] != -1) {
            return false;
        }
        //We are (probably) going to be baking the block id
        // check that it is currently not inflight, if it is, return as its already being baked
        // else add it to the flight as it is going to be baked
        this.blockStatesInFlightLock.lock();
        if (!this.blockStatesInFlight.add(blockId)) {
            this.blockStatesInFlightLock.unlock();
            //Block baking is already in-flight
            return false;
        }
        this.blockStatesInFlightLock.unlock();

        VarHandle.loadLoadFence();

        //We need to get it twice cause of threading
        if (this.idMappings[blockId] != -1) {
            return false;
        }

        var blockState = this.mapper.getBlockStateFromBlockId(blockId);

        if (blockState.getBlock() instanceof StairBlock sb) {
            BlockState baseState = null;
            if (STAIR_BASE_STATE_FIELD != null) {
                try {
                    baseState = (BlockState) STAIR_BASE_STATE_FIELD.get(sb);
                } catch (Exception e) {
                    Logger.error("Failed to get StairBlock baseState via reflection: " + e.getMessage());
                }
            }

            // Если рефлексия сработала, используем полученное состояние
            if (baseState != null) {
                if (baseState.hasProperty(BlockStateProperties.WATERLOGGED)) {
                    blockState = baseState.setValue(BlockStateProperties.WATERLOGGED, blockState.getValue(BlockStateProperties.WATERLOGGED));
                } else {
                    blockState = baseState;
                }
            }
        }

        //Before we enqueue the baking of this blockstate, we must check if it has a fluid state associated with it
        // if it does, we must ensure that it is (effectivly) baked BEFORE we bake this blockstate
        boolean isFluid = blockState.getBlock() instanceof LiquidBlock;
        if ((!isFluid) && (!blockState.getFluidState().isEmpty())) {
            //Insert into the fluid LUT
            var fluidState = blockState.getFluidState().createLegacyBlock();

            int fluidStateId = this.mapper.getIdForBlockState(fluidState);

            if (this.idMappings[fluidStateId] == -1) {
                //Dont have to check for inflight as that is done recursively :p

                //This is a hack but does work :tm: due to how the download stream is setup
                // it should enforce that the fluid state is processed before our blockstate
                addEntry(fluidStateId);
            }
        }
        this.bakeQueue.add(new BlockBake(blockId, blockState));
        return true;
    }

    private boolean processModelResult() {
        var bake = this.bakeQueue.poll();
        if (bake == null) return false;
        ColourDepthTextureData[] textureData = new ColourDepthTextureData[6];

        int flags = this.bakery2.renderToOutput(bake.state, this.bakeScratchBuffer);

        boolean hasDarkenedTextures = (flags&2)!=0;
        boolean isShaded = (flags&1)!=0;

        {//Create texture data
            long ptr = this.bakeScratchBuffer;
            //long ptr = result.rawData.address;
            final int FACE_SIZE = MODEL_TEXTURE_SIZE * MODEL_TEXTURE_SIZE;
            for (int face = 0; face < 6; face++) {
                long faceDataPtr = ptr + (FACE_SIZE * 4) * face * 2;
                int[] colour = new int[FACE_SIZE];
                int[] depth = new int[FACE_SIZE];

                //Copy out colour
                for (int i = 0; i < FACE_SIZE; i++) {
                    ////De-interpolate results
                    //colour[i] = MemoryUtil.memGetInt(faceDataPtr + (i * 4 * 2));
                    //depth[i] = MemoryUtil.memGetInt(faceDataPtr + (i * 4 * 2) + 4);

                    long value = MemoryUtil.memGetLong(faceDataPtr+i*8);
                    colour[i] = (int)value;
                    depth[i] = (int) (value>>>32);
                }
                textureData[face] = new ColourDepthTextureData(colour, depth, MODEL_TEXTURE_SIZE, MODEL_TEXTURE_SIZE);
            }
        }

        var bakeResult = this.processTextureBakeResult(bake.blockId, bake.state, textureData, isShaded, hasDarkenedTextures);
        if (bakeResult!=null) {
            this.uploadResults.add(bakeResult);
        }
        return !this.bakeQueue.isEmpty();
    }

    private final ConcurrentLinkedDeque<Mapper.BiomeEntry> biomeQueue = new ConcurrentLinkedDeque<>();
    public void addBiome(Mapper.BiomeEntry biome) {
        this.biomeQueue.add(biome);
    }

    public void processAllThings() {
        var biomeEntry = this.biomeQueue.poll();
        while (biomeEntry != null) {
            var biomeRegistry = Minecraft.getInstance().level.registryAccess().registryOrThrow(Registries.BIOME);
            var mcbiomeEntry = biomeRegistry.getOptional(ResourceLocation.tryParse(biomeEntry.biome));
            if (!mcbiomeEntry.isPresent()) {
                Logger.error("Could not find biome: " + biomeEntry.biome + " using default");
            }
            var res = this.addBiome0(biomeEntry.id, mcbiomeEntry.orElse(DEFAULT_BIOME));
            if (res != null) {
                this.uploadResults.add(res);
            }
            biomeEntry = this.biomeQueue.poll();
        }

        while (this.processModelResult());
    }

    public void processUploads() {
        var upload = this.uploadResults.poll();
        if (upload==null) return;

        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        do {
            upload.upload(this.storage);
            upload.free();
            upload = this.uploadResults.poll();
        } while (upload != null);
        UploadStream.INSTANCE.commit();
    }

    private interface ResultUploader {
        void upload(ModelStore store);
        void free();
    }

    private static final class ModelBakeResultUpload implements ResultUploader {
        private final MemoryBuffer model = new MemoryBuffer(MODEL_SIZE).zero();
        private final MemoryBuffer texture = new MemoryBuffer((2L*3*computeSizeWithMips(MODEL_TEXTURE_SIZE))*4);

        public int modelId = -1;

        public int biomeUploadIndex = -1;
        public @Nullable MemoryBuffer biomeUpload;

        public void upload(ModelStore store) {//Uploads and resets for reuse
            this.upload(store.modelBuffer, store.modelColourBuffer, store.textures);
        }

        public void upload(GlBuffer modelBuffer, GlBuffer colourBuffer, GlTexture atlas) {//Uploads and resets for reuse
            this.model.cpyTo(UploadStream.INSTANCE.upload(modelBuffer, (long) this.modelId * MODEL_SIZE, MODEL_SIZE));
            if (this.biomeUploadIndex != -1) {
                this.biomeUpload.cpyTo(UploadStream.INSTANCE.upload(colourBuffer, this.biomeUploadIndex * 4L, this.biomeUpload.size));
                this.biomeUploadIndex = -1;
                this.biomeUpload.free();
                this.biomeUpload = null;
            }

            int X = (this.modelId&0xFF) * MODEL_TEXTURE_SIZE*3;
            int Y = ((this.modelId>>8)&0xFF) * MODEL_TEXTURE_SIZE*2;

            long cAddr = this.texture.address;
            for (int lvl = 0; lvl < LAYERS; lvl++) {
                nglTextureSubImage2D(atlas.id, lvl, X >> lvl, Y >> lvl, (MODEL_TEXTURE_SIZE*3) >> lvl, (MODEL_TEXTURE_SIZE*2) >> lvl, GL_RGBA, GL_UNSIGNED_BYTE, cAddr);
                cAddr += (MODEL_TEXTURE_SIZE*MODEL_TEXTURE_SIZE*3*2*4)>>(lvl<<1);
            }

            this.modelId = -1;
        }

        public void free() {
            this.model.free();
            this.texture.free();
            if (this.biomeUpload != null) {
                this.biomeUpload.free();
            }
        }
    }

    private ModelBakeResultUpload processTextureBakeResult(int blockId, BlockState blockState, ColourDepthTextureData[] textureData, boolean isShaded, boolean darkenedTinting) {
        if (this.idMappings[blockId] != -1) {
            //This should be impossible to reach as it means that multiple bakes for the same blockId happened and where inflight at the same time!
            throw new IllegalStateException("Block id already added: " + blockId + " for state: " + blockState);
        }

        this.blockStatesInFlightLock.lock();
        if (!this.blockStatesInFlight.contains(blockId)) {
            this.blockStatesInFlightLock.unlock();
            throw new IllegalStateException("processing a texture bake result but the block state was not in flight!!");
        }
        this.blockStatesInFlightLock.unlock();

        //TODO: add thing for `blockState.hasEmissiveLighting()` and `blockState.getLuminance()`

        boolean isFluid = blockState.getBlock() instanceof LiquidBlock;
        int modelId = -1;


        int clientFluidStateId = -1;

        if ((!isFluid) && (!blockState.getFluidState().isEmpty())) {
            //Insert into the fluid LUT
            var fluidState = blockState.getFluidState().createLegacyBlock();

            int fluidStateId = this.mapper.getIdForBlockState(fluidState);

            clientFluidStateId = this.idMappings[fluidStateId];
            if (clientFluidStateId == -1) {
                throw new IllegalStateException("Block has a fluid state but fluid state is not already baked!!!");
            }
        }

        var colourProvider = getColourProvider(blockState.getBlock());

        boolean isBiomeColourDependent = false;
        if (colourProvider != null) {
            isBiomeColourDependent = isBiomeDependentColour(colourProvider, blockState);
        }

        ModelEntry entry;
        {//Deduplicate same entries
            entry = new ModelEntry(textureData, clientFluidStateId, isBiomeColourDependent||colourProvider==null?-1:captureColourConstant(colourProvider, blockState, DEFAULT_BIOME)|0xFF000000);
            int possibleDuplicate = this.modelTexture2id.getInt(entry);
            if (possibleDuplicate != -1) {//Duplicate found
                this.idMappings[blockId] = possibleDuplicate;
                modelId = possibleDuplicate;
                //Remove from flight
                this.blockStatesInFlightLock.lock();
                if (!this.blockStatesInFlight.remove(blockId)) {
                    this.blockStatesInFlightLock.unlock();
                    throw new IllegalStateException();
                }
                this.blockStatesInFlightLock.unlock();
                return null;
            } else {//Not a duplicate so create a new entry
                modelId = this.modelTexture2id.size();
                //NOTE: we set the mapping at the very end so that race conditions with this and getMetadata dont occur
                //this.idMappings[blockId] = modelId;
                this.modelTexture2id.put(entry, modelId);
            }
        }

        if (isFluid) {
            this.fluidStateLUT[modelId] = modelId;
        } else if (clientFluidStateId != -1) {
            this.fluidStateLUT[modelId] = clientFluidStateId;
        }

        RenderType blockRenderLayer = null;
        if (blockState.getBlock() instanceof LiquidBlock) {
            blockRenderLayer = ItemBlockRenderTypes.getRenderLayer(blockState.getFluidState());
        } else {
            if (blockState.getBlock() instanceof LeavesBlock) {
                blockRenderLayer = RenderType.solid();
            } else {
                blockRenderLayer = ItemBlockRenderTypes.getChunkRenderType(blockState);
            }
        }


        int checkMode = blockRenderLayer==RenderType.solid()?TextureUtils.WRITE_CHECK_STENCIL:TextureUtils.WRITE_CHECK_ALPHA;




        ModelBakeResultUpload uploadResult = new ModelBakeResultUpload();
        uploadResult.modelId = modelId;
        long uploadPtr = uploadResult.model.address;

        //TODO: implement;
        // TODO: if it has a constant colour instead... idk why (apparently for things like spruce leaves)?? but premultiply the texture data by the constant colour

        //If it contains fluid but isnt a fluid
        if ((!isFluid) && (!blockState.getFluidState().isEmpty()) && clientFluidStateId != -1) {

            //Or it with the fluid state biome dependency
            isBiomeColourDependent |= ModelQueries.isBiomeColoured(this.getModelMetadataFromClientId(clientFluidStateId));
        }



        //TODO: special case stuff like vines and glow lichen, where it can be represented by a single double sided quad
        // since that would help alot with perf of lots of vines, can be done by having one of the faces just not exist and the other be in no occlusion mode

        var depths = computeModelDepth(textureData, checkMode, blockRenderLayer!=RenderType.solid()?TextureUtils.DEPTH_MODE_MIN:TextureUtils.DEPTH_MODE_AVG);

        //TODO: THIS, note this can be tested for in 2 ways, re render the model with quad culling disabled and see if the result
        // is the same, (if yes then needs double sided quads)
        // another way to test it is if e.g. up and down havent got anything rendered but the sides do (e.g. all plants etc)
        boolean needsDoubleSidedQuads = (depths[0] < -0.1 && depths[1] < -0.1) || (depths[2] < -0.1 && depths[3] < -0.1) || (depths[4] < -0.1 && depths[5] < -0.1);


        boolean cullsSame = false;

        {
            //TODO: Could also move this into the RenderDataFactory and do it on the actual blockstates instead of a guestimation
            boolean allTrue = true;
            boolean allFalse = true;
            //Guestimation test for if the block culls itself
            for (var dir : Direction.values()) {
                if (blockState.skipRendering(blockState, dir)) {
                    allFalse = false;
                } else {
                    allTrue = false;
                }
            }

            if (allFalse == allTrue) {//If only some sides where self culled then abort
                cullsSame = false;
                //if (LOGGED_SELF_CULLING_WARNING.add(blockState))
                //    Logger.info("Warning! blockstate: " + blockState + " only culled against its self some of the time");
            }

            if (allTrue) {
                cullsSame = true;
            }
        }


        //Each face gets 1 byte, with the top 2 bytes being for whatever
        long metadata = 0;
        metadata |= isBiomeColourDependent?1:0;
        metadata |= blockRenderLayer == RenderType.translucent()?2:0;
        metadata |= needsDoubleSidedQuads?4:0;
        metadata |= ((!isFluid) && !blockState.getFluidState().isEmpty())?8:0;//Has a fluid state accosiacted with it and is not itself a fluid
        metadata |= isFluid?16:0;//Is a fluid

        metadata |= cullsSame?32:0;

        boolean fullyOpaque = true;

        //TODO: FIXME faces that have the same "alignment depth" e.g. (sizes[0]+sizes[1])~=1 can be merged into a double faced single quad

        //TODO: add a bunch of control config options for overriding/setting options of metadata for each face of each type
        for (int face = 5; face != -1; face--) {//In reverse order to make indexing into the metadata long easier
            long faceUploadPtr = uploadPtr + 4L * face;//Each face gets 4 bytes worth of data
            metadata <<= 8;
            float offset = depths[face];
            if (offset < -0.1) {//Face is empty, so ignore
                metadata |= 0xFF;//Mark the face as non-existent
                //Set to -1 as safepoint
                MemoryUtil.memPutInt(faceUploadPtr, -1);

                fullyOpaque = false;
                continue;
            }
            var faceSize = TextureUtils.computeBounds(textureData[face], checkMode);
            int writeCount = TextureUtils.getWrittenPixelCount(textureData[face], checkMode);

            boolean faceCoversFullBlock = faceSize[0] == 0 && faceSize[2] == 0 &&
                    faceSize[1] == (MODEL_TEXTURE_SIZE-1) && faceSize[3] == (MODEL_TEXTURE_SIZE-1);

            //TODO: use faceSize and the depths to compute if mesh can be correctly rendered

            metadata |= faceCoversFullBlock?2:0;

            //TODO: add alot of config options for the following
            boolean occludesFace = true;
            occludesFace &= blockRenderLayer != RenderType.translucent();//If its translucent, it doesnt occlude

            //TODO: make this an option, basicly if the face is really close, it occludes otherwise it doesnt
            occludesFace &= offset < 0.1;//If the face is rendered far away from the other face, then it doesnt occlude

            if (occludesFace) {
                occludesFace &= ((float)writeCount)/(MODEL_TEXTURE_SIZE * MODEL_TEXTURE_SIZE) > 0.9;// only occlude if the face covers more than 90% of the face
            }
            metadata |= occludesFace?1:0;
            fullyOpaque &= occludesFace;



            boolean canBeOccluded = true;
            //TODO: make this an option on how far/close
            canBeOccluded &= offset < 0.3;//If the face is rendered far away from the other face, then it cant be occluded

            metadata |= canBeOccluded?4:0;

            //Face uses its own lighting if its not flat against the adjacent block & isnt traslucent
            metadata |= (offset > 0.01 || blockRenderLayer == RenderType.translucent())?0b1000:0;



            if (MODEL_TEXTURE_SIZE-1 != 15) {
                //Scale face size from 0->this.modelTextureSize-1 to 0->15
                for (int i = 0; i < 4; i++) {
                    faceSize[i] = Math.round((((float) faceSize[i]) / (MODEL_TEXTURE_SIZE - 1)) * 15);
                }
            }

            int faceModelData = 0;
            faceModelData |= faceSize[0] | (faceSize[1]<<4) | (faceSize[2]<<8) | (faceSize[3]<<12);
            //Change the scale from 0->1 (ends inclusive)
            // this is cursed also warning stuff at 63 (i.e half a pixel from the end will be clamped to the end)
            int enc = Math.round(offset*64);
            faceModelData |= Math.min(enc,62)<<16;
            //Still have 11 bits free

            //Stuff like fences are solid, however they have extra side piece that mean it needs to have discard on
            int area = (faceSize[1]-faceSize[0]+1) * (faceSize[3]-faceSize[2]+1);
            boolean needsAlphaDiscard = ((float)writeCount)/area<0.9;//If the amount of area covered by written pixels is less than a threashold, disable discard as its not needed

            needsAlphaDiscard |= blockRenderLayer != RenderType.solid();
            needsAlphaDiscard &= blockRenderLayer != RenderType.translucent();//Translucent doesnt have alpha discard
            faceModelData |= needsAlphaDiscard?1<<22:0;

            faceModelData |= ((!faceCoversFullBlock)&&blockRenderLayer != RenderType.translucent())?1<<23:0;//Alpha discard override, translucency doesnt have alpha discard

            //Bits 24,25 are tint metadata
            if (colourProvider!=null) {//We have a tint
                int tintState = TextureUtils.computeFaceTint(textureData[face], checkMode);
                if (tintState == 2) {//Partial tint
                    faceModelData |= 1<<24;
                } else if (tintState == 3) {//Full tint
                    faceModelData |= 2<<24;
                }
            }

            MemoryUtil.memPutInt(faceUploadPtr, faceModelData);
        }

        metadata |= fullyOpaque?(1L<<(48+6)):0;

        boolean canBeCorrectlyRendered = true;//This represents if a model can be correctly (perfectly) represented
        // i.e. no gaps

        this.metadataCache[modelId] = metadata;

        uploadPtr += 4*6;
        //Have 40 bytes free for remaining model data
        // todo: put in like the render layer type ig? along with colour resolver info
        int modelFlags = 0;
        modelFlags |= colourProvider != null?1:0;
        modelFlags |= isBiomeColourDependent?2:0;//Basicly whether to use the next int as a colour or as a base index/id into a colour buffer for biome dependent colours
        modelFlags |= blockRenderLayer == RenderType.translucent()?4:0;//Is translucent


        //TODO: THIS
        modelFlags |= isShaded?8:0;//model has AO and shade

        //modelFlags |= blockRenderLayer == RenderLayer.getSolid()?0:1;// should discard alpha
        MemoryUtil.memPutInt(uploadPtr, modelFlags); uploadPtr += 4;


        //Temporary override to always be non biome specific
        if (colourProvider == null) {
            MemoryUtil.memPutInt(uploadPtr, -1);//Set the default to nothing so that its faster on the gpu
        } else if (!isBiomeColourDependent) {
            MemoryUtil.memPutInt(uploadPtr, entry.tintingColour);
        } else if (!this.biomes.isEmpty()) {
            //Populate the list of biomes for the model state
            int biomeIndex = this.modelsRequiringBiomeColours.size() * this.biomes.size();
            MemoryUtil.memPutInt(uploadPtr, biomeIndex);
            this.modelsRequiringBiomeColours.add(new Pair<>(modelId, blockState));

            uploadResult.biomeUploadIndex = biomeIndex;
            long clrUploadPtr = (uploadResult.biomeUpload = new MemoryBuffer(4L * this.biomes.size())).address;
            for (var biome : this.biomes) {
                MemoryUtil.memPutInt(clrUploadPtr, captureColourConstant(colourProvider, blockState, biome)|0xFF000000); clrUploadPtr += 4;
            }
        }
        uploadPtr += 4;

        //have 32 bytes of free space after here

        //install the custom mapping id if it exists
        if (this.customBlockStateIdMapping != null && this.customBlockStateIdMapping.containsKey(blockState)) {
            MemoryUtil.memPutInt(uploadPtr, this.customBlockStateIdMapping.getInt(blockState));
        } else {
            MemoryUtil.memPutInt(uploadPtr, 0);
        } uploadPtr += 4;


        //Note: if the layer isSolid then need to fill all the points in the texture where alpha == 0 with the average colour
        // of the surrounding blocks but only within the computed face size bounds

        //TODO callback to inject extra data into the model data


        MipGen.putTextures(darkenedTinting, textureData, uploadResult.texture);

        //glGenerateTextureMipmap(this.textures.id);

        //Set the mapping at the very end
        this.idMappings[blockId] = modelId;

        this.blockStatesInFlightLock.lock();
        if (!this.blockStatesInFlight.remove(blockId)) {
            this.blockStatesInFlightLock.unlock();
            throw new IllegalStateException("processing a texture bake result but the block state was not in flight!!");
        }
        this.blockStatesInFlightLock.unlock();

        return uploadResult;
    }

    private static final class BiomeUploadResult implements ResultUploader {
        private final MemoryBuffer biomeColourBuffer;
        private final MemoryBuffer modelBiomeIndexPairs;
        private BiomeUploadResult(int biomes, int models) {
            this.biomeColourBuffer = new MemoryBuffer(biomes*models*4);
            this.modelBiomeIndexPairs = new MemoryBuffer(models*8);
        }

        public void upload(ModelStore store) {
            this.upload(store.modelBuffer, store.modelColourBuffer);
        }

        public void upload(GlBuffer modelBuffer, GlBuffer modelColourBuffer) {
            this.biomeColourBuffer.cpyTo(UploadStream.INSTANCE.upload(modelColourBuffer, 0, this.biomeColourBuffer.size));

            //TODO: optimize this to like a compute scatter update or something
            long ptr = this.modelBiomeIndexPairs.address;
            for (long offset = 0; offset < this.modelBiomeIndexPairs.size; offset += 8) {
                long v = MemoryUtil.memGetLong(ptr);ptr += 8;
                MemoryUtil.memPutInt(UploadStream.INSTANCE.upload(modelBuffer, (MODEL_SIZE*(v&((1L<<32)-1)))+ 4*6 + 4, 4), (int) (v>>>32));
            }

            this.biomeColourBuffer.free();
            this.modelBiomeIndexPairs.free();
        }

        public void free() {
            if (!this.biomeColourBuffer.isFreed()) {
                this.biomeColourBuffer.free();
                this.modelBiomeIndexPairs.free();
            }
        }
    }

    private BiomeUploadResult addBiome0(int id, Biome biome) {
        if (biome == null) {
            throw new IllegalStateException("Null biome");
        }
        for (int i = this.biomes.size(); i <= id; i++) {
            this.biomes.add(null);
        }
        var oldBiome = this.biomes.set(id, biome);

        if (oldBiome != null && oldBiome != biome) {
            throw new IllegalStateException("Biome was put in an id that was not null");
        }
        if (oldBiome == biome) {
            Logger.error("Biome added was a duplicate: " + id);
            return null;
        }

        if (this.modelsRequiringBiomeColours.isEmpty()) return null;

        var result = new BiomeUploadResult(this.biomes.size(), this.modelsRequiringBiomeColours.size());

        int i = 0;
        long modelUpPtr = result.modelBiomeIndexPairs.address;
        for (var entry : this.modelsRequiringBiomeColours) {
            var colourProvider = getColourProvider(entry.right().getBlock());
            if (colourProvider == null) {
                throw new IllegalStateException();
            }
            //Populate the list of biomes for the model state
            int biomeIndex = (i++) * this.biomes.size();
            MemoryUtil.memPutLong(modelUpPtr, Integer.toUnsignedLong(entry.left())|(Integer.toUnsignedLong(biomeIndex)<<32));modelUpPtr+=8;
            long clrUploadPtr = result.biomeColourBuffer.address + biomeIndex * 4L;
            for (var biomeE : this.biomes) {
                if (biomeE == null) {
                    continue;//If null, ignore
                }
                MemoryUtil.memPutInt(clrUploadPtr, captureColourConstant(colourProvider, entry.right(), biomeE)|0xFF000000); clrUploadPtr += 4;
            }
        }

        return result;
    }

    private static BlockColor getColourProvider(Block block) {
        return Minecraft.getInstance().getBlockColors().blockColors.byId(BuiltInRegistries.BLOCK.getId(block));
    }

    //TODO: add a method to detect biome dependent colours (can do by detecting if getColor is ever called)
    // if it is, need to add it to a list and mark it as biome colour dependent or something then the shader
    // will either use the uint as an index or a direct colour multiplier
    private static int captureColourConstant(BlockColor colorProvider, BlockState state, Biome biome) {
        var getter = new BlockAndTintGetter() {
            @Override
            public float getShade(Direction direction, boolean shaded) {
                return 0;
            }

            @Override
            public int getBrightness(LightLayer type, BlockPos pos) {
                return 0;
            }

            @Override
            public LevelLightEngine getLightEngine() {
                return null;
            }

            @Override
            public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
                if (colorResolver == null) {
                    Logger.error("Block state: " + state + " colourprovider: " + colorProvider + " had a null colorresolver");
                    return 0;
                }
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
            public int getMinBuildHeight() {
                return 0;
            }
        };
        //Multiple layer bs to do with flower beds
        int c = colorProvider.getColor(state, getter, BlockPos.ZERO, 0);
        if (c!=-1) return c;
        return colorProvider.getColor(state, getter, BlockPos.ZERO, 1);
    }

    private static boolean isBiomeDependentColour(BlockColor colorProvider, BlockState state) {
        boolean[] biomeDependent = new boolean[1];
        var getter = new BlockAndTintGetter() {
            @Override
            public float getShade(Direction direction, boolean shaded) {
                return 0;
            }

            @Override
            public int getBrightness(LightLayer type, BlockPos pos) {
                return 0;
            }

            @Override
            public LevelLightEngine getLightEngine() {
                return null;
            }

            @Override
            public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
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
            public int getMinBuildHeight() {
                return 0;
            }
        };
        colorProvider.getColor(state, getter, BlockPos.ZERO, 0);
        colorProvider.getColor(state, getter, BlockPos.ZERO, 1);
        return biomeDependent[0];
    }

    private static float[] computeModelDepth(ColourDepthTextureData[] textures, int checkMode) {
        return computeModelDepth(textures, checkMode, TextureUtils.DEPTH_MODE_AVG);
    }

    private static float[] computeModelDepth(ColourDepthTextureData[] textures, int checkMode, int computeMode) {
        float[] res = new float[6];
        for (var dir : Direction.values()) {
            var data = textures[dir.get3DDataValue()];
            float fd = TextureUtils.computeDepth(data, computeMode, checkMode);//Compute the min float depth, smaller means closer to the camera, range 0-1
            //int depth = Math.round(fd * MODEL_TEXTURE_SIZE);
            //If fd is -1, it means that there was nothing rendered on that face and it should be discarded
            if (fd < -0.1) {
                res[dir.ordinal()] = -1;
            } else {
                res[dir.ordinal()] = fd;//((float) depth)/MODEL_TEXTURE_SIZE;
            }
        }
        return res;
    }

    public int[] _unsafeRawAccess() {
        return this.idMappings;
    }

    public int getModelId(int blockId) {
        int map = this.idMappings[blockId];
        if (map == -1) {
            throw new IdNotYetComputedException(blockId, true);
        }
        return map;
    }

    public boolean hasModelForBlockId(int blockId) {
        return this.idMappings[blockId] != -1;
    }

    public int getFluidClientStateId(int clientBlockStateId) {
        int map = this.fluidStateLUT[clientBlockStateId];
        if (map == -1) {
            throw new IdNotYetComputedException(clientBlockStateId, false);
        }
        return map;
    }

    public final long getModelMetadataFromClientId(int clientId) {
        return this.metadataCache[clientId];
    }


    public void free() {
        this.bakery2.free();
        MemoryUtil.nmemFree(this.bakeScratchBuffer);
        while (!this.uploadResults.isEmpty()) {
            this.uploadResults.poll().free();
        }
    }

    public int getBakedCount() {
        return this.modelTexture2id.size();
    }

    public int getInflightCount() {
        //TODO replace all of this with an atomic?
        int size = this.blockStatesInFlight.size();
        size += this.uploadResults.size();
        size += this.biomeQueue.size();
        size += this.bakeQueue.size();
        return size;
    }


    private static int computeSizeWithMips(int size) {
        int total = 0;
        for (;size!=0;size>>=1) total += size*size;
        return total;
    }
}