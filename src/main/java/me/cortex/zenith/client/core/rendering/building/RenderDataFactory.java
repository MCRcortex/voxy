package me.cortex.zenith.client.core.rendering.building;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import me.cortex.zenith.client.core.model.ModelManager;
import me.cortex.zenith.client.core.util.Mesher2D;
import me.cortex.zenith.common.util.MemoryBuffer;
import me.cortex.zenith.common.world.WorldEngine;
import me.cortex.zenith.common.world.WorldSection;
import me.cortex.zenith.common.world.other.Mapper;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.system.MemoryUtil;

import java.util.Map;


public class RenderDataFactory {
    private final WorldEngine world;
    private final ModelManager modelMan;
    private final QuadEncoder encoder;
    private final long[] sectionCache = new long[32*32*32];
    private final long[] connectedSectionCache = new long[32*32*32];
    public RenderDataFactory(WorldEngine world, ModelManager modelManager) {
        this.world = world;
        this.modelMan = modelManager;
        this.encoder = new QuadEncoder(world.getMapper(), MinecraftClient.getInstance().getBlockColors(), MinecraftClient.getInstance().world);
    }


    //TODO: MAKE a render cache that caches each WorldSection directional face generation, cause then can just pull that directly
    // instead of needing to regen the entire thing


    //section is already acquired and gets released by the parent

    //buildMask in the lower 6 bits contains the faces to build, the next 6 bits are whether the edge face builds against
    // its neigbor or not (0 if it does 1 if it doesnt (0 is default behavior))
    public BuiltSection generateMesh(WorldSection section, int buildMask) {
        section.copyDataTo(this.sectionCache);

        //TODO:NOTE! when doing face culling of translucent blocks,
        // if the connecting type of the translucent block is the same AND the face is full, discard it
        // this stops e.g. multiple layers of glass (and ocean) from having 3000 layers of quads etc


        Mesher2D mesher = new Mesher2D(5,15);

        LongArrayList outData = new LongArrayList(1000);

        //Up direction
        for (int y = 0; y < 32; y++) {
            mesher.reset();
            for (int x = 0; x < 32; x++) {
                for (int z = 0; z < 32; z++) {
                    long self = this.sectionCache[WorldSection.getIndex(x, y, z)];
                    if (Mapper.isAir(self)) continue;

                    int selfBlockId = Mapper.getBlockId(self);
                    long metadata = this.modelMan.getModelMetadata(selfBlockId);

                    //If the model doesnt have a face, then just skip it
                    if (!ModelManager.faceExists(metadata, 1)) {
                        continue;
                    }

                    long facingState = Mapper.AIR;
                    //Need to access the other connecting section
                    if (y == 31) {

                    } else {
                        facingState = this.sectionCache[WorldSection.getIndex(x, y+1, z)];
                    }

                    long facingMetadata = this.modelMan.getModelMetadata(Mapper.getBlockId(facingState));

                    //If face can be occluded and is occluded from the facing block, then dont render the face
                    if (ModelManager.faceCanBeOccluded(metadata, 1) && ModelManager.faceOccludes(facingMetadata, 0)) {
                        continue;
                    }

                    int clientModelId = this.modelMan.getModelId(selfBlockId);

                    mesher.put(x, z, ((long)clientModelId) | (((long) Mapper.getLightId(facingState))<<16) | (((long) Mapper.getBiomeId(self))<<24));
                }
            }

            //TODO: encode translucents and double sided quads to different global buffers
            int count = mesher.process();
            var array = mesher.getArray();
            for (int i = 0; i < count; i++) {
                int quad = array[i];
                long data = mesher.getDataFromQuad(quad);
                outData.add(Integer.toUnsignedLong(QuadEncoder.encodePosition(1, y, quad)) | ((data&0xFFFF)<<26) | (((data>>16)&0xFF)<<55) | (((data>>24)&0x1FF)<<46));
            }
        }


        //North direction
        for (int z = 0; z < 32; z++) {
            mesher.reset();
            for (int x = 0; x < 32; x++) {
                for (int y = 0; y < 32; y++) {
                    long self = this.sectionCache[WorldSection.getIndex(x, y, z)];
                    if (Mapper.isAir(self)) continue;

                    int selfBlockId = Mapper.getBlockId(self);
                    long metadata = this.modelMan.getModelMetadata(selfBlockId);

                    //If the model doesnt have a face, then just skip it
                    if (!ModelManager.faceExists(metadata, 2)) {
                        continue;
                    }

                    long facingState = Mapper.AIR;
                    //Need to access the other connecting section
                    if (z == 0) {

                    } else {
                        facingState = this.sectionCache[WorldSection.getIndex(x, y, z-1)];
                    }

                    long facingMetadata = this.modelMan.getModelMetadata(Mapper.getBlockId(facingState));

                    //If face can be occluded and is occluded from the facing block, then dont render the face
                    if (ModelManager.faceCanBeOccluded(metadata, 2) && ModelManager.faceOccludes(facingMetadata, 3)) {
                        continue;
                    }

                    int clientModelId = this.modelMan.getModelId(selfBlockId);

                    mesher.put(x, y, ((long)clientModelId) | (((long) Mapper.getLightId(facingState))<<16) | (((long) Mapper.getBiomeId(self))<<24));
                }
            }

            //TODO: encode translucents and double sided quads to different global buffers
            int count = mesher.process();
            var array = mesher.getArray();
            for (int i = 0; i < count; i++) {
                int quad = array[i];
                long data = mesher.getDataFromQuad(quad);
                outData.add(Integer.toUnsignedLong(QuadEncoder.encodePosition(2, z, quad)) | ((data&0xFFFF)<<26) | (((data>>16)&0xFF)<<55) | (((data>>24)&0x1FF)<<46));
            }
        }

        //South direction
        for (int z = 0; z < 32; z++) {
            mesher.reset();
            for (int x = 0; x < 32; x++) {
                for (int y = 0; y < 32; y++) {
                    long self = this.sectionCache[WorldSection.getIndex(x, y, z)];
                    if (Mapper.isAir(self)) continue;

                    int selfBlockId = Mapper.getBlockId(self);
                    long metadata = this.modelMan.getModelMetadata(selfBlockId);

                    //If the model doesnt have a face, then just skip it
                    if (!ModelManager.faceExists(metadata, 3)) {
                        continue;
                    }

                    long facingState = Mapper.AIR;
                    //Need to access the other connecting section
                    if (z == 31) {

                    } else {
                        facingState = this.sectionCache[WorldSection.getIndex(x, y, z+1)];
                    }

                    long facingMetadata = this.modelMan.getModelMetadata(Mapper.getBlockId(facingState));

                    //If face can be occluded and is occluded from the facing block, then dont render the face
                    if (ModelManager.faceCanBeOccluded(metadata, 3) && ModelManager.faceOccludes(facingMetadata, 2)) {
                        continue;
                    }

                    int clientModelId = this.modelMan.getModelId(selfBlockId);

                    mesher.put(x, y, ((long)clientModelId) | (((long) Mapper.getLightId(facingState))<<16) | (((long) Mapper.getBiomeId(self))<<24));
                }
            }

            //TODO: encode translucents and double sided quads to different global buffers
            int count = mesher.process();
            var array = mesher.getArray();
            for (int i = 0; i < count; i++) {
                int quad = array[i];
                long data = mesher.getDataFromQuad(quad);
                outData.add(Integer.toUnsignedLong(QuadEncoder.encodePosition(3, z, quad)) | ((data&0xFFFF)<<26) | (((data>>16)&0xFF)<<55) | (((data>>24)&0x1FF)<<46));
            }
        }

        //West direction
        for (int x = 0; x < 32; x++) {
            mesher.reset();
            for (int y = 0; y < 32; y++) {
                for (int z = 0; z < 32; z++) {
                    long self = this.sectionCache[WorldSection.getIndex(x, y, z)];
                    if (Mapper.isAir(self)) continue;

                    int selfBlockId = Mapper.getBlockId(self);
                    long metadata = this.modelMan.getModelMetadata(selfBlockId);

                    //If the model doesnt have a face, then just skip it
                    if (!ModelManager.faceExists(metadata, 2)) {
                        continue;
                    }

                    long facingState = Mapper.AIR;
                    //Need to access the other connecting section
                    if (x == 0) {

                    } else {
                        facingState = this.sectionCache[WorldSection.getIndex(x-1, y, z)];
                    }

                    long facingMetadata = this.modelMan.getModelMetadata(Mapper.getBlockId(facingState));

                    //If face can be occluded and is occluded from the facing block, then dont render the face
                    if (ModelManager.faceCanBeOccluded(metadata, 4) && ModelManager.faceOccludes(facingMetadata, 5)) {
                        continue;
                    }

                    int clientModelId = this.modelMan.getModelId(selfBlockId);

                    mesher.put(y, z, ((long)clientModelId) | (((long) Mapper.getLightId(facingState))<<16) | (((long) Mapper.getBiomeId(self))<<24));
                }
            }

            //TODO: encode translucents and double sided quads to different global buffers
            int count = mesher.process();
            var array = mesher.getArray();
            for (int i = 0; i < count; i++) {
                int quad = array[i];
                long data = mesher.getDataFromQuad(quad);
                outData.add(Integer.toUnsignedLong(QuadEncoder.encodePosition(4, x, quad)) | ((data&0xFFFF)<<26) | (((data>>16)&0xFF)<<55) | (((data>>24)&0x1FF)<<46));
            }
        }

        //East direction
        for (int x = 0; x < 32; x++) {
            mesher.reset();
            for (int y = 0; y < 32; y++) {
                for (int z = 0; z < 32; z++) {
                    long self = this.sectionCache[WorldSection.getIndex(x, y, z)];
                    if (Mapper.isAir(self)) continue;

                    int selfBlockId = Mapper.getBlockId(self);
                    long metadata = this.modelMan.getModelMetadata(selfBlockId);

                    //If the model doesnt have a face, then just skip it
                    if (!ModelManager.faceExists(metadata, 2)) {
                        continue;
                    }

                    long facingState = Mapper.AIR;
                    //Need to access the other connecting section
                    if (x == 31) {

                    } else {
                        facingState = this.sectionCache[WorldSection.getIndex(x+1, y, z)];
                    }

                    long facingMetadata = this.modelMan.getModelMetadata(Mapper.getBlockId(facingState));

                    //If face can be occluded and is occluded from the facing block, then dont render the face
                    if (ModelManager.faceCanBeOccluded(metadata, 5) && ModelManager.faceOccludes(facingMetadata, 4)) {
                        continue;
                    }

                    int clientModelId = this.modelMan.getModelId(selfBlockId);

                    mesher.put(y, z, ((long)clientModelId) | (((long) Mapper.getLightId(facingState))<<16) | (((long) Mapper.getBiomeId(self))<<24));
                }
            }

            //TODO: encode translucents and double sided quads to different global buffers
            int count = mesher.process();
            var array = mesher.getArray();
            for (int i = 0; i < count; i++) {
                int quad = array[i];
                long data = mesher.getDataFromQuad(quad);
                outData.add(Integer.toUnsignedLong(QuadEncoder.encodePosition(5, x, quad)) | ((data&0xFFFF)<<26) | (((data>>16)&0xFF)<<55) | (((data>>24)&0x1FF)<<46));
            }
        }





        //var buff = new MemoryBuffer(8*8);
        //MemoryUtil.memPutLong(buff.address,        encodeRaw(2, 0,1,0,0,0,159,0, 0));//92 515
        //MemoryUtil.memPutLong(buff.address+8,  encodeRaw(3, 0,1,0,0,0,159,0, 0));//92 515
        //MemoryUtil.memPutLong(buff.address+16, encodeRaw(4, 1,2,0,0,0,159,0, 0));//92 515
        //MemoryUtil.memPutLong(buff.address+24, encodeRaw(5, 1,2,0,0,0,159,0, 0));//92 515
        //MemoryUtil.memPutLong(buff.address+32, encodeRaw(2, 0,1,0,0,1,159,0, 0));//92 515
        //MemoryUtil.memPutLong(buff.address+40, encodeRaw(3, 0,1,0,0,1,159,0, 0));//92 515
        //MemoryUtil.memPutLong(buff.address+48, encodeRaw(2, 0,1,0,0,2,159,0, 0));//92 515
        //MemoryUtil.memPutLong(buff.address+56, encodeRaw(3, 0,1,0,0,2,159,0, 0));//92 515
        if (outData.isEmpty()) {
            return new BuiltSection(section.getKey());
        }
        //outData.clear();

        //int modelId = this.modelMan.getModelId(this.world.getMapper().getIdFromBlockState(Blocks.OAK_FENCE.getDefaultState()));
        int modelId = this.modelMan.getModelId(this.world.getMapper().getIdFromBlockState(Blocks.OAK_FENCE.getDefaultState()));

        //outData.add(encodeRaw(0, 0,0,0,0,0, modelId,0, 0));
        //outData.add(encodeRaw(1, 0,0,0,0,0, modelId,0, 0));
        //outData.add(encodeRaw(2, 0,0,0,0,0, modelId,0, 0));
        //outData.add(encodeRaw(3, 0,0,0,0,0, modelId,0, 0));
        //outData.add(encodeRaw(4, 0,0,0,0,0, modelId,0, 0));
        //outData.add(encodeRaw(5, 0,0,0,0,0, modelId,0, 0));

        var buff = new MemoryBuffer(outData.size()*8L);
        long ptr = buff.address;
        for (long data : outData) {
            MemoryUtil.memPutLong(ptr, data); ptr+=8;
        }

        return new BuiltSection(section.getKey(), (31<<15)|(31<<20)|(31<<25), buff, new int[]{0, outData.size(), outData.size(), outData.size(), outData.size(), outData.size(), outData.size(), outData.size()});
    }


    private static long encodeRaw(int face, int width, int height, int x, int y, int z, int blockId, int biomeId, int lightId) {
        return ((long)face) | (((long) width)<<3) | (((long) height)<<7) | (((long) z)<<11) | (((long) y)<<16) | (((long) x)<<21) | (((long) blockId)<<26) | (((long) biomeId)<<46) | (((long) lightId)<<55);
    }


    /*
    private void generateMeshForAxis() {

        //Up direction
        for (int y = 0; y < 32; y++) {
            mesher.reset();
            for (int x = 0; x < 32; x++) {
                for (int z = 0; z < 32; z++) {
                    long self = this.sectionCache[WorldSection.getIndex(x, y, z)];
                    if (Mapper.isAir(self)) continue;

                    int selfBlockId = Mapper.getBlockId(self);
                    long metadata = this.modelMan.getModelMetadata(selfBlockId);

                    //If the model doesnt have a face, then just skip it
                    if (!ModelManager.faceExists(metadata, 1)) {
                        continue;
                    }

                    long facingState = Mapper.AIR;
                    //Need to access the other connecting section
                    if (y == 31) {

                    } else {
                        facingState = this.sectionCache[WorldSection.getIndex(x, y+1, z)];
                    }

                    long facingMetadata = this.modelMan.getModelMetadata(Mapper.getBlockId(facingState));

                    //If face can be occluded and is occluded from the facing block, then dont render the face
                    if (ModelManager.faceCanBeOccluded(metadata, 1) && ModelManager.faceOccludes(facingMetadata, 0)) {
                        continue;
                    }

                    int clientModelId = this.modelMan.getModelId(selfBlockId);

                    mesher.put(x, z, ((long)clientModelId) | (((long) Mapper.getLightId(facingState))<<16) | (((long) Mapper.getBiomeId(self))<<24));
                }
            }

            //TODO: encode translucents and double sided quads to different global buffers
            int count = mesher.process();
            var array = mesher.getArray();
            for (int i = 0; i < count; i++) {
                int quad = array[i];
                long data = mesher.getDataFromQuad(quad);
                outData.add(Integer.toUnsignedLong(QuadEncoder.encodePosition(1, y, quad)) | ((data&0xFFFF)<<26) | (((data>>16)&0xFF)<<55) | (((data>>24)&0x1FF)<<46));
            }
        }
    }*/

}
