package me.cortex.voxy.client.core.rendering.building;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import me.cortex.voxy.client.core.Capabilities;
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
    private final Mesher2D negativeFluidMesher = new Mesher2D(5, 15);
    private final Mesher2D positiveFluidMesher = new Mesher2D(5, 15);

    private final long[] sectionCache = new long[32*32*32];
    private final long[] connectedSectionCache = new long[32*32*32];

    private final LongArrayList doubleSidedQuadCollector = new LongArrayList();
    private final LongArrayList translucentQuadCollector = new LongArrayList();
    private final LongArrayList[] directionalQuadCollectors = new LongArrayList[]{new LongArrayList(), new LongArrayList(), new LongArrayList(), new LongArrayList(), new LongArrayList(), new LongArrayList()};

    private final boolean generateMeshlets;

    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;
    public RenderDataFactory(WorldEngine world, ModelManager modelManager, boolean emitMeshlets) {
        this.world = world;
        this.modelMan = modelManager;
        this.generateMeshlets = emitMeshlets;
    }


    //TODO: MAKE a render cache that caches each WorldSection directional face generation, cause then can just pull that directly
    // instead of needing to regen the entire thing


    //Ok so the idea for fluid rendering is to make it use a seperate mesher and use a different code path for it
    // since fluid states are explicitly overlays over the base block
    // can do funny stuff like double rendering

    private static final boolean USE_UINT64 = Capabilities.INSTANCE.INT64_t;
    public static final int QUADS_PER_MESHLET = 62;
    private static void writePos(long ptr, long pos) {
        if (USE_UINT64) {
            MemoryUtil.memPutLong(ptr, pos);
        } else {
            MemoryUtil.memPutInt(ptr, (int) (pos>>32));
            MemoryUtil.memPutInt(ptr + 4, (int)pos);
        }
    }

    //section is already acquired and gets released by the parent
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

        int bufferSize;
        if (this.generateMeshlets) {
            bufferSize = getMeshletHoldingCount(this.doubleSidedQuadCollector.size(), QUADS_PER_MESHLET, QUADS_PER_MESHLET+2) +
                    getMeshletHoldingCount(this.translucentQuadCollector.size(), QUADS_PER_MESHLET, QUADS_PER_MESHLET+2);
            for (var collector : this.directionalQuadCollectors) {
                bufferSize += getMeshletHoldingCount(collector.size(), QUADS_PER_MESHLET, QUADS_PER_MESHLET+2);
            }
        } else {
            bufferSize = this.doubleSidedQuadCollector.size() + this.translucentQuadCollector.size();
            for (var collector : this.directionalQuadCollectors) {
                bufferSize += collector.size();
            }
        }

        if (bufferSize == 0) {
            return new BuiltSection(section.key);
        }

        //TODO: generate the meshlets here
        MemoryBuffer buff;
        int[] offsets = new int[8];
        if (this.generateMeshlets) {
            long key = section.key;
            buff = new MemoryBuffer(bufferSize * 8L);
            long ptr = buff.address;
            MemoryUtil.memSet(ptr, 0,bufferSize * 8L);
            int meshlet = 0;
            int innerQuadCount = 0;

            //Ordering is: translucent, double sided quads, directional quads
            offsets[0] = meshlet;
            int mix = 32, miy = 32, miz = 32, max = 0, may = 0, maz = 0;
            for (long data : this.translucentQuadCollector) {
                if (innerQuadCount == 0) {
                    //Write out meshlet header

                    //Write out the section position
                    writePos(ptr + meshlet * 8L * (QUADS_PER_MESHLET+2), key);
                }
                MemoryUtil.memPutLong(ptr + meshlet * 8L * (QUADS_PER_MESHLET+2) + (2 + innerQuadCount++) * 8L, data);
                int x = QuadEncoder.getX(data), y = QuadEncoder.getY(data), z = QuadEncoder.getZ(data), f = QuadEncoder.getFace(data);
                mix = Math.min(x, mix); miy = Math.min(y, miy); miz = Math.min(z, miz);
                if ((f>>1)==0) {
                    max = Math.max(x + QuadEncoder.getW(data), max);
                    may = Math.max(y, may);
                    maz = Math.max(z + QuadEncoder.getH(data), maz);
                } else if ((f>>1)==1) {
                    max = Math.max(x + QuadEncoder.getW(data), max);
                    may = Math.max(y + QuadEncoder.getH(data), may);
                    maz = Math.max(z, maz);
                } else {
                    max = Math.max(x, max);
                    may = Math.max(y + QuadEncoder.getW(data), may);
                    maz = Math.max(z + QuadEncoder.getH(data), maz);
                }



                if (innerQuadCount == QUADS_PER_MESHLET) {
                    //Write out the meshlet size data
                    long sizeData = ((long)mix)|(((long)miy)<<8)|(((long)miz)<<16)|
                            (((long)max)<<24)|(((long)may)<<32)|(((long)maz)<<40);
                    writePos(ptr + meshlet * 8L * (QUADS_PER_MESHLET+2) + 8, sizeData);


                    innerQuadCount = 0;
                    meshlet++;
                    mix = 32; miy = 32; miz = 32; max = 0; may = 0; maz = 0;
                }
            }

            if (innerQuadCount != 0) {
                //Write out the meshlet size data
                long sizeData = ((long)mix)|(((long)miy)<<8)|(((long)miz)<<16)|
                        (((long)max)<<24)|(((long)may)<<32)|(((long)maz)<<40);
                writePos(ptr + meshlet * 8L * (QUADS_PER_MESHLET+2) + 8, sizeData);

                meshlet++;
                innerQuadCount = 0;
                mix = 32; miy = 32; miz = 32; max = 0; may = 0; maz = 0;
            }

            offsets[1] = meshlet;
            for (long data : this.doubleSidedQuadCollector) {
                if (innerQuadCount == 0) {
                    //Write out meshlet header

                    //Write out the section position
                    writePos(ptr + meshlet * 8L * (QUADS_PER_MESHLET+2), key);
                }
                MemoryUtil.memPutLong(ptr + meshlet * 8L * (QUADS_PER_MESHLET+2) + (2 + innerQuadCount++) * 8L, data);
                int x = QuadEncoder.getX(data), y = QuadEncoder.getY(data), z = QuadEncoder.getZ(data), f = QuadEncoder.getFace(data);
                mix = Math.min(x, mix); miy = Math.min(y, miy); miz = Math.min(z, miz);
                if ((f>>1)==0) {
                    max = Math.max(x + QuadEncoder.getW(data), max);
                    may = Math.max(y, may);
                    maz = Math.max(z + QuadEncoder.getH(data), maz);
                } else if ((f>>1)==1) {
                    max = Math.max(x + QuadEncoder.getW(data), max);
                    may = Math.max(y + QuadEncoder.getH(data), may);
                    maz = Math.max(z, maz);
                } else {
                    max = Math.max(x, max);
                    may = Math.max(y + QuadEncoder.getW(data), may);
                    maz = Math.max(z + QuadEncoder.getH(data), maz);
                }
                if (innerQuadCount == QUADS_PER_MESHLET) {
                    //Write out the meshlet size data
                    long sizeData = ((long)mix)|(((long)miy)<<8)|(((long)miz)<<16)|
                            (((long)max)<<24)|(((long)may)<<32)|(((long)maz)<<40);
                    writePos(ptr + meshlet * 8L * (QUADS_PER_MESHLET+2) + 8, sizeData);


                    innerQuadCount = 0;
                    meshlet++;
                    mix = 32; miy = 32; miz = 32; max = 0; may = 0; maz = 0;
                }
            }

            if (innerQuadCount != 0) {
                //Write out the meshlet size data
                long sizeData = ((long)mix)|(((long)miy)<<8)|(((long)miz)<<16)|
                        (((long)max)<<24)|(((long)may)<<32)|(((long)maz)<<40);
                writePos(ptr + meshlet * 8L * (QUADS_PER_MESHLET+2) + 8, sizeData);


                meshlet++;
                innerQuadCount = 0;
                mix = 32; miy = 32; miz = 32; max = 0; may = 0; maz = 0;
            }

            for (int face = 0; face < 6; face++) {
                offsets[face + 2] = meshlet;
                for (long data : this.directionalQuadCollectors[face]) {
                    if (innerQuadCount == 0) {
                        //Write out meshlet header

                        //Write out the section position
                        writePos(ptr + meshlet * 8L * (QUADS_PER_MESHLET+2), key);
                    }
                    MemoryUtil.memPutLong(ptr + meshlet * 8L * (QUADS_PER_MESHLET+2) + (2 + innerQuadCount++) * 8L, data);
                    int x = QuadEncoder.getX(data), y = QuadEncoder.getY(data), z = QuadEncoder.getZ(data), f = QuadEncoder.getFace(data);
                    mix = Math.min(x, mix); miy = Math.min(y, miy); miz = Math.min(z, miz);
                    if ((f>>1)==0) {
                        max = Math.max(x + QuadEncoder.getW(data), max);
                        may = Math.max(y, may);
                        maz = Math.max(z + QuadEncoder.getH(data), maz);
                    } else if ((f>>1)==1) {
                        max = Math.max(x + QuadEncoder.getW(data), max);
                        may = Math.max(y + QuadEncoder.getH(data), may);
                        maz = Math.max(z, maz);
                    } else {
                        max = Math.max(x, max);
                        may = Math.max(y + QuadEncoder.getW(data), may);
                        maz = Math.max(z + QuadEncoder.getH(data), maz);
                    }
                    if (innerQuadCount == QUADS_PER_MESHLET) {
                        //Write out the meshlet size data
                        long sizeData = ((long)mix)|(((long)miy)<<8)|(((long)miz)<<16)|
                                (((long)max)<<24)|(((long)may)<<32)|(((long)maz)<<40);
                        writePos(ptr + meshlet * 8L * (QUADS_PER_MESHLET+2) + 8, sizeData);


                        innerQuadCount = 0;
                        meshlet++;
                        mix = 32; miy = 32; miz = 32; max = 0; may = 0; maz = 0;
                    }
                }

                if (innerQuadCount != 0) {
                    //Write out the meshlet size data
                    long sizeData = ((long)mix)|(((long)miy)<<8)|(((long)miz)<<16)|
                            (((long)max)<<24)|(((long)may)<<32)|(((long)maz)<<40);
                    writePos(ptr + meshlet * 8L * (QUADS_PER_MESHLET+2) + 8, sizeData);


                    meshlet++;
                    innerQuadCount = 0;
                    mix = 32; miy = 32; miz = 32; max = 0; may = 0; maz = 0;
                }
            }
        } else {
            buff = new MemoryBuffer(bufferSize * 8L);
            long ptr = buff.address;
            int coff = 0;

            //Ordering is: translucent, double sided quads, directional quads
            offsets[0] = coff;
            for (long data : this.translucentQuadCollector) {
                MemoryUtil.memPutLong(ptr + ((coff++) * 8L), data);
            }

            offsets[1] = coff;
            for (long data : this.doubleSidedQuadCollector) {
                MemoryUtil.memPutLong(ptr + ((coff++) * 8L), data);
            }

            for (int face = 0; face < 6; face++) {
                offsets[face + 2] = coff;
                for (long data : this.directionalQuadCollectors[face]) {
                    MemoryUtil.memPutLong(ptr + ((coff++) * 8L), data);
                }
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


    //TODO: FIXME: a block can have a face even if it doesnt, cause of if it has a fluid state
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
            this.negativeFluidMesher.reset();
            this.positiveFluidMesher.reset();

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
                    if (ModelManager.faceExists(selfMetadata, axisId<<1) || ModelManager.containsFluid(selfMetadata)) {//- direction
                        long facingState = Mapper.AIR;
                        //Need to access the other connecting section
                        if (primary == 0) {
                            if (!obtainedOppositeSection0) {
                                var connectedSection = this.world.acquireIfExists(section.lvl, section.x - aX, section.y - aY, section.z - aZ);
                                if (connectedSection != null) {
                                    connectedSection.copyDataTo(this.connectedSectionCache);
                                    connectedSection.release();
                                } else {
                                    Arrays.fill(this.connectedSectionCache, Mapper.withLight(Mapper.AIR, 15));
                                }
                                obtainedOppositeSection0 = true;
                            }
                            facingState = this.connectedSectionCache[WorldSection.getIndex(x*(1-aX)+(31*aX), y*(1-aY)+(31*aY), z*(1-aZ)+(31*aZ))];
                        } else {
                            facingState = this.sectionCache[WorldSection.getIndex(x-aX, y-aY, z-aZ)];
                        }

                        if (!ModelManager.isFluid(selfMetadata)) {
                            putFace |= this.putFaceIfCan(this.negativeMesher, (axisId << 1), (axisId << 1)|1, self, selfMetadata, selfBlockId, facingState, a, b);
                        }
                        if (ModelManager.containsFluid(selfMetadata)) {
                            putFace |= this.putFluidFaceIfCan(this.negativeFluidMesher, (axisId << 1), (axisId << 1)|1, self, selfMetadata, selfBlockId, facingState, a, b);
                        }
                    }
                    if (ModelManager.faceExists(selfMetadata, (axisId<<1)|1) || ModelManager.containsFluid(selfMetadata)) {//+ direction
                        long facingState = Mapper.AIR;
                        //Need to access the other connecting section
                        if (primary == 31) {
                            if (!obtainedOppositeSection31) {
                                var connectedSection = this.world.acquireIfExists(section.lvl, section.x + aX, section.y + aY, section.z + aZ);
                                if (connectedSection != null) {
                                    connectedSection.copyDataTo(this.connectedSectionCache);
                                    connectedSection.release();
                                } else {
                                    Arrays.fill(this.connectedSectionCache, Mapper.withLight(Mapper.AIR, 15));
                                }
                                obtainedOppositeSection31 = true;
                            }
                            facingState = this.connectedSectionCache[WorldSection.getIndex(x*(1-aX), y*(1-aY), z*(1-aZ))];
                        } else {
                            facingState = this.sectionCache[WorldSection.getIndex(x+aX, y+aY, z+aZ)];
                        }
                        if (!ModelManager.isFluid(selfMetadata)) {
                            putFace |= this.putFaceIfCan(this.positiveMesher, (axisId << 1) | 1, (axisId << 1), self, selfMetadata, selfBlockId, facingState, a, b);
                        }
                        if (ModelManager.containsFluid(selfMetadata)) {
                            putFace |= this.putFluidFaceIfCan(this.positiveFluidMesher, (axisId << 1) | 1, (axisId << 1), self, selfMetadata, selfBlockId, facingState, a, b);
                        }
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

            processMeshedFace(this.negativeFluidMesher, axisId<<1,     primary, this.directionalQuadCollectors[(axisId<<1)]);
            processMeshedFace(this.positiveFluidMesher, (axisId<<1)|1, primary, this.directionalQuadCollectors[(axisId<<1)|1]);
        }
    }



    //Returns true if a face was placed
    private boolean putFluidFaceIfCan(Mesher2D mesher, int face, int opposingFace, long self, long metadata, int selfBlockId, long facingState, int a, int b) {
        long facingMetadata = this.modelMan.getModelMetadata(Mapper.getBlockId(facingState));

        int selfFluidClientId = this.modelMan.getFluidClientStateId(this.modelMan.getModelId(selfBlockId));
        long selfFluidMetadata = this.modelMan.getModelMetadataFromClientId(selfFluidClientId);

        int facingFluidClientId = -1;
        if (ModelManager.containsFluid(facingMetadata)) {
            facingFluidClientId = this.modelMan.getFluidClientStateId(this.modelMan.getModelId(Mapper.getBlockId(facingState)));
        }

        //If both of the states are the same, then dont render the fluid face
        if (selfFluidClientId == facingFluidClientId) {
            return false;
        }

        if (facingFluidClientId != -1) {
            if (this.world.getMapper().getBlockStateFromBlockId(selfBlockId).getBlock() == this.world.getMapper().getBlockStateFromBlockId(Mapper.getBlockId(facingState)).getBlock()) {
               return false;
            }
        }


        if (ModelManager.faceOccludes(facingMetadata, opposingFace)) {
            return false;
        }

        //if the model has a fluid state but is not a liquid need to see if the solid state had a face rendered and that face is occluding, if so, dont render the fluid state face
        if ((!ModelManager.isFluid(metadata)) && ModelManager.faceOccludes(metadata, face)) {
            return false;
        }




        //TODO:FIXME SOMEHOW THIS IS CRITICAL!!!!!!!!!!!!!!!!!!
        // so there is one more issue need to be fixed, if water is layered ontop of eachother, the side faces depend on the water state ontop
        // this has been hackfixed in the model texture bakery but a proper solution that doesnt explode the sides of the water textures needs to be done
        // the issue is that the fluid rendering depends on the up state aswell not just the face state which is really really painful to account for
        // e.g the sides of a full water is 8 high or something, not the full block height, this results in a gap between water layers



        long otherFlags = 0;
        otherFlags |= ModelManager.isTranslucent(selfFluidMetadata)?1L<<33:0;
        otherFlags |= ModelManager.isDoubleSided(selfFluidMetadata)?1L<<34:0;
        mesher.put(a, b, ((long)selfFluidClientId) | (((long) Mapper.getLightId(ModelManager.faceUsesSelfLighting(selfFluidMetadata, face)?self:facingState))<<16) | ((((long) Mapper.getBiomeId(self))<<24) * (ModelManager.isBiomeColoured(selfFluidMetadata)?1:0)) | otherFlags);
        return true;
    }

    //Returns true if a face was placed
    private boolean putFaceIfCan(Mesher2D mesher, int face, int opposingFace, long self, long metadata, int selfBlockId, long facingState, int a, int b) {
        long facingMetadata = this.modelMan.getModelMetadata(Mapper.getBlockId(facingState));

        //If face can be occluded and is occluded from the facing block, then dont render the face
        if (ModelManager.faceCanBeOccluded(metadata, face) && ModelManager.faceOccludes(facingMetadata, opposingFace)) {
            return false;
        }

        if (ModelManager.cullsSame(metadata) && selfBlockId == Mapper.getBlockId(facingState)) {
            //If we are facing a block, and we are both the same state, dont render that face
            return false;
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

    private static int getMeshletHoldingCount(int quads, int quadsPerMeshlet, int meshletSize) {
        return ((quads+(quadsPerMeshlet-1))/quadsPerMeshlet)*meshletSize;
    }

    public static int alignUp(int n, int alignment) {
        return (n + alignment - 1) & -alignment;
    }
}