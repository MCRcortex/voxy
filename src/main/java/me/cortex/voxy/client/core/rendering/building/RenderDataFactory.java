package me.cortex.voxy.client.core.rendering.building;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import me.cortex.voxy.client.core.model.ModelManager;
import me.cortex.voxy.client.core.util.Mesher2D;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.block.FluidBlock;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Array;
import java.util.Arrays;


public class RenderDataFactory {
    private final WorldEngine world;
    private final ModelManager modelMan;

    private final Mesher2D negativeMesher = new Mesher2D(5, 15);
    private final Mesher2D positiveMesher = new Mesher2D(5, 15);

    private final long[] sectionCache = new long[32*32*32];
    private final long[] connectedSectionCache = new long[32*32*32];

    private final LongArrayList doubleSidedQuadCollector = new LongArrayList();
    private final LongArrayList translucentQuadCollector = new LongArrayList();
    private final LongArrayList[] directionalQuadCollectors = new LongArrayList[]{new LongArrayList(), new LongArrayList(), new LongArrayList(), new LongArrayList(), new LongArrayList(), new LongArrayList()};


    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;
    public RenderDataFactory(WorldEngine world, ModelManager modelManager) {
        this.world = world;
        this.modelMan = modelManager;
    }


    //TODO: MAKE a render cache that caches each WorldSection directional face generation, cause then can just pull that directly
    // instead of needing to regen the entire thing


    //section is already acquired and gets released by the parent

    //buildMask in the lower 6 bits contains the faces to build, the next 6 bits are whether the edge face builds against
    // its neigbor or not (0 if it does 1 if it doesnt (0 is default behavior))
    public BuiltSection generateMesh(WorldSection section) {
        section.copyDataTo(this.sectionCache);
        this.translucentQuadCollector.clear();
        this.doubleSidedQuadCollector.clear();
        for (var collector : this.directionalQuadCollectors) {
            collector.clear();
        }
        this.minX = Integer.MAX_VALUE;
        this.minY = Integer.MAX_VALUE;
        this.minZ = Integer.MAX_VALUE;
        this.maxX = Integer.MIN_VALUE;
        this.maxY = Integer.MIN_VALUE;
        this.maxZ = Integer.MIN_VALUE;

        //TODO:NOTE! when doing face culling of translucent blocks,
        // if the connecting type of the translucent block is the same AND the face is full, discard it
        // this stops e.g. multiple layers of glass (and ocean) from having 3000 layers of quads etc


        this.generateMeshForAxis(section, 0);//Direction.Axis.Y
        this.generateMeshForAxis(section, 1);//Direction.Axis.Z
        this.generateMeshForAxis(section, 2);//Direction.Axis.X

        int quadCount = this.doubleSidedQuadCollector.size() + this.translucentQuadCollector.size();
        for (var collector : this.directionalQuadCollectors) {
            quadCount += collector.size();
        }

        if (quadCount == 0) {
            return new BuiltSection(section.key);
        }

        var buff = new MemoryBuffer(quadCount*8L);
        long ptr = buff.address;
        int[] offsets = new int[8];
        int coff = 0;

        //Ordering is: translucent, double sided quads, directional quads
        offsets[0] = coff;
        for (long data : this.translucentQuadCollector) {
            MemoryUtil.memPutLong(ptr + ((coff++)*8L), data);
        }

        offsets[1] = coff;
        for (long data : this.doubleSidedQuadCollector) {
            MemoryUtil.memPutLong(ptr + ((coff++)*8L), data);
        }

        for (int face = 0; face < 6; face++) {
            offsets[face+2] = coff;
            for (long data : this.directionalQuadCollectors[face]) {
                MemoryUtil.memPutLong(ptr + ((coff++) * 8L), data);
            }
        }

        int aabb = 0;
        aabb |= this.minX;
        aabb |= this.minY<<5;
        aabb |= this.minZ<<10;
        aabb |= (this.maxX-this.minX)<<15;
        aabb |= (this.maxY-this.minY)<<20;
        aabb |= (this.maxZ-this.minZ)<<25;

        return new BuiltSection(section.key, aabb, buff, offsets);
    }


    private void generateMeshForAxis(WorldSection section, int axisId) {
        int aX = axisId==2?1:0;
        int aY = axisId==0?1:0;
        int aZ = axisId==1?1:0;

        //Note the way the connectedSectionCache works is that it reuses the section cache because we know we dont need the connectedSection
        // when we are on the other direction
        boolean obtainedOppositeSection0  = false;
        boolean obtainedOppositeSection31 = false;


        for (int primary = 0; primary < 32; primary++) {
            this.negativeMesher.reset();
            this.positiveMesher.reset();

            for (int a = 0; a < 32; a++) {
                for (int b = 0; b < 32; b++) {
                    int x = axisId==2?primary:a;
                    int y = axisId==0?primary:(axisId==1?b:a);
                    int z = axisId==1?primary:b;
                    long self = this.sectionCache[WorldSection.getIndex(x,y,z)];
                    if (Mapper.isAir(self)) continue;

                    int selfBlockId = Mapper.getBlockId(self);
                    long selfMetadata = this.modelMan.getModelMetadata(selfBlockId);



                    boolean putFace = false;

                    //Branch into 2 paths, the + direction and -direction, doing it at once makes it much faster as it halves the number of loops

                    if (ModelManager.faceExists(selfMetadata, axisId<<1)) {//- direction
                        long facingState = Mapper.AIR;
                        //Need to access the other connecting section
                        if (primary == 0) {
                            if (!obtainedOppositeSection0) {
                                var connectedSection = this.world.acquireIfExists(section.lvl, section.x - aX, section.y - aY, section.z - aZ);
                                if (connectedSection != null) {
                                    connectedSection.copyDataTo(this.connectedSectionCache);
                                    connectedSection.release();
                                } else {
                                    Arrays.fill(this.connectedSectionCache, 0);
                                }
                                obtainedOppositeSection0 = true;
                            }
                            facingState = this.connectedSectionCache[WorldSection.getIndex(x*(1-aX)+(31*aX), y*(1-aY)+(31*aY), z*(1-aZ)+(31*aZ))];
                        } else {
                            facingState = this.sectionCache[WorldSection.getIndex(x-aX, y-aY, z-aZ)];
                        }

                        putFace |= this.putFaceIfCan(this.negativeMesher, (axisId<<1), (axisId<<1)|1, self, selfMetadata, selfBlockId, facingState, a, b);
                    }
                    if (ModelManager.faceExists(selfMetadata, axisId<<1)) {//+ direction
                        long facingState = Mapper.AIR;
                        //Need to access the other connecting section
                        if (primary == 31) {
                            if (!obtainedOppositeSection31) {
                                var connectedSection = this.world.acquireIfExists(section.lvl, section.x + aX, section.y + aY, section.z + aZ);
                                if (connectedSection != null) {
                                    connectedSection.copyDataTo(this.connectedSectionCache);
                                    connectedSection.release();
                                } else {
                                    Arrays.fill(this.connectedSectionCache, 0);
                                }
                                obtainedOppositeSection31 = true;
                            }
                            facingState = this.connectedSectionCache[WorldSection.getIndex(x*(1-aX), y*(1-aY), z*(1-aZ))];
                        } else {
                            facingState = this.sectionCache[WorldSection.getIndex(x+aX, y+aY, z+aZ)];
                        }

                        putFace |= this.putFaceIfCan(this.positiveMesher, (axisId<<1)|1, (axisId<<1), self, selfMetadata, selfBlockId, facingState, a, b);
                    }

                    if (putFace) {
                        this.minX = Math.min(this.minX, x);
                        this.minY = Math.min(this.minY, y);
                        this.minZ = Math.min(this.minZ, z);
                        this.maxX = Math.max(this.maxX, x);
                        this.maxY = Math.max(this.maxY, y);
                        this.maxZ = Math.max(this.maxZ, z);
                    }
                }
            }

            processMeshedFace(this.negativeMesher, axisId<<1,     primary, this.directionalQuadCollectors[(axisId<<1)]);
            processMeshedFace(this.positiveMesher, (axisId<<1)|1, primary, this.directionalQuadCollectors[(axisId<<1)|1]);
        }
    }

    //Returns true if a face was placed
    private boolean putFaceIfCan(Mesher2D mesher, int face, int opposingFace, long self, long metadata, int selfBlockId, long facingState, int a, int b) {
        long facingMetadata = this.modelMan.getModelMetadata(Mapper.getBlockId(facingState));

        //If face can be occluded and is occluded from the facing block, then dont render the face
        if (ModelManager.faceCanBeOccluded(metadata, face) && ModelManager.faceOccludes(facingMetadata, opposingFace)) {
            return false;
        }

        if (ModelManager.isTranslucent(metadata) && selfBlockId == Mapper.getBlockId(facingState)) {
            //If we are facing a block, and are translucent and it is the same block as us, cull the quad
            return false;
        }


        //TODO: if the model has a fluid state, it should compute if a fluid face needs to be injected
        // fluid face of type this.world.getMapper().getBlockStateFromId(self).getFluidState() and block type
        // this.world.getMapper().getBlockStateFromId(self).getFluidState().getBlockState()

        //If we are a fluid
        if (ModelManager.containsFluid(metadata)) {
            var selfBS = this.world.getMapper().getBlockStateFromId(self);
            if (ModelManager.containsFluid(facingMetadata)) {//and the oposing face is also a fluid need to make a closer check
                var faceBS = this.world.getMapper().getBlockStateFromId(facingState);

                //If we are a fluid block that means our face is a fluid face, waterlogged blocks dont include fluid faces in the model data
                if (selfBS.getBlock() instanceof FluidBlock) {
                    //If the fluid state of both blocks are the same we dont emit extra geometry
                    if (selfBS.getFluidState().getBlockState().equals(faceBS.getFluidState().getBlockState())) {
                        return false;
                    }
                } else {//If we are not a fluid block, we might need to emit extra geometry (fluid faces) to the auxliery mesher
                    boolean shouldEmitFluidFace = !selfBS.getFluidState().getBlockState().equals(faceBS.getFluidState().getBlockState());
                    //TODO: THIS
                    int aa = 0;
                }
            } else if (!(selfBS.getBlock() instanceof FluidBlock)) {//If we are not a fluid block but we contain a fluid we might need to emit extra geometry
                //Basicly need to get the fluid state and run putFaceIfCan using the fluid state as the self state and keep the same facing state
                //TODO: THIS
                int aa = 0;
            }
        }



        int clientModelId = this.modelMan.getModelId(selfBlockId);
        long otherFlags = 0;
        otherFlags |= ModelManager.isTranslucent(metadata)?1L<<33:0;
        otherFlags |= ModelManager.isDoubleSided(metadata)?1L<<34:0;
        mesher.put(a, b, ((long)clientModelId) | (((long) Mapper.getLightId(ModelManager.faceUsesSelfLighting(metadata, face)?self:facingState))<<16) | ((((long) Mapper.getBiomeId(self))<<24) * (ModelManager.isBiomeColoured(metadata)?1:0)) | otherFlags);
        return true;
    }

    private void processMeshedFace(Mesher2D mesher, int face, int otherAxis, LongArrayList axisOutputGeometry) {
        //TODO: encode translucents and double sided quads to different global buffers

        int count = mesher.process();
        var array = mesher.getArray();
        for (int i = 0; i < count; i++) {
            int quad = array[i];
            long data = mesher.getDataFromQuad(quad);
            long encodedQuad = Integer.toUnsignedLong(QuadEncoder.encodePosition(face, otherAxis, quad)) | ((data&0xFFFF)<<26) | (((data>>16)&0xFF)<<55) | (((data>>24)&0x1FF)<<46);


            if ((data&(1L<<33))!=0) {
                this.translucentQuadCollector.add(encodedQuad);
            } else if ((data&(1L<<34))!=0) {
                this.doubleSidedQuadCollector.add(encodedQuad);
            } else {
                axisOutputGeometry.add(encodedQuad);
            }
        }
    }
}


/*
    private static long encodeRaw(int face, int width, int height, int x, int y, int z, int blockId, int biomeId, int lightId) {
        return ((long)face) | (((long) width)<<3) | (((long) height)<<7) | (((long) z)<<11) | (((long) y)<<16) | (((long) x)<<21) | (((long) blockId)<<26) | (((long) biomeId)<<46) | (((long) lightId)<<55);
    }
 */


/*

        //outData.clear();

        //var buff = new MemoryBuffer(8*8);
        //MemoryUtil.memPutLong(buff.address,        encodeRaw(2, 0,1,0,0,0,159,0, 0));//92 515
        //MemoryUtil.memPutLong(buff.address+8,  encodeRaw(3, 0,1,0,0,0,159,0, 0));//92 515
        //MemoryUtil.memPutLong(buff.address+16, encodeRaw(4, 1,2,0,0,0,159,0, 0));//92 515
        //MemoryUtil.memPutLong(buff.address+24, encodeRaw(5, 1,2,0,0,0,159,0, 0));//92 515
        //MemoryUtil.memPutLong(buff.address+32, encodeRaw(2, 0,1,0,0,1,159,0, 0));//92 515
        //MemoryUtil.memPutLong(buff.address+40, encodeRaw(3, 0,1,0,0,1,159,0, 0));//92 515
        //MemoryUtil.memPutLong(buff.address+48, encodeRaw(2, 0,1,0,0,2,159,0, 0));//92 515
        //MemoryUtil.memPutLong(buff.address+56, encodeRaw(3, 0,1,0,0,2,159,0, 0));//92 515

        //int modelId = this.modelMan.getModelId(this.world.getMapper().getIdFromBlockState(Blocks.OAK_FENCE.getDefaultState()));
        //int modelId = this.modelMan.getModelId(this.world.getMapper().getIdFromBlockState(Blocks.OAK_FENCE.getDefaultState()));

        //outData.add(encodeRaw(0, 0,0,0,0,0, modelId,0, 0));
        //outData.add(encodeRaw(1, 0,0,0,0,0, modelId,0, 0));
        //outData.add(encodeRaw(2, 0,0,0,0,0, modelId,0, 0));
        //outData.add(encodeRaw(3, 0,0,0,0,0, modelId,0, 0));
        //outData.add(encodeRaw(4, 0,0,0,0,0, modelId,0, 0));
        //outData.add(encodeRaw(5, 0,0,0,0,0, modelId,0, 0));
 */



/*


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
 */