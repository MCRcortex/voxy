package me.cortex.voxelmon.client.core.rendering.building;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import me.cortex.voxelmon.common.util.MemoryBuffer;
import me.cortex.voxelmon.client.core.util.Mesher2D;
import me.cortex.voxelmon.common.world.WorldEngine;
import me.cortex.voxelmon.common.world.WorldSection;
import me.cortex.voxelmon.common.world.other.Mapper;
import net.minecraft.util.math.Direction;
import org.lwjgl.system.MemoryUtil;


public class RenderDataFactory2 {
    private final Mesher2D[] meshers = new Mesher2D[6];
    private final LongArrayList outData = new LongArrayList(1000);
    private final WorldEngine world;
    private final long[] sectionCache = new long[32*32*32];
    private final long[][] connectedSectionCaches = new long[6][32*32*32];

    public RenderDataFactory2(WorldEngine world) {
        this.world = world;
        for (int i = 0; i < this.meshers.length; i++) {
            this.meshers[i] = new Mesher2D(5, 15);
        }
    }

    //TODO: MAKE a render cache that caches each WorldSection directional face generation, cause then can just pull that directly
    // instead of needing to regen the entire thing

    //section is already acquired and gets released by the parent

    //buildMask in the lower 6 bits contains the faces to build, the next 6 bits are whether the edge face builds against
    // its neigbor or not (0 if it does 1 if it doesnt (0 is default behavior))

    public BuiltSectionGeometry generateMesh2(WorldSection section, int buildMask) {
        //TODO: to speed it up more, check like section.isEmpty() and stuff like that, have masks for if a slice/layer is entirly air etc

        //TODO: instead of having it check its neighbors with the same lod level, compare against 1 level lower, this will prevent cracks and seams from
        // appearing between lods


        //if (section.definitelyEmpty()) {//Fast path if its known the entire chunk is empty
        //    return new BuiltSectionGeometry(section.getKey(), null, null);
        //}

        section.copyDataTo(this.sectionCache);
        var data = this.sectionCache;

        long[][] localConnections = new long[6][];

        for (int y = 0; y < 32; y++) {
            for (int z = 0; z < 32; z++) {
                for (int x = 0; x < 32; x++) {
                    var self = data[WorldSection.getIndex(x, y, z)];
                    if (Mapper.isAir(self)) {
                        continue;
                    }

                    //TODO: FInish
                    // whats missing/is an issue is that having multiple meshers at once with an arbitary render direction doesnt really work
                    // Need to majorly fix this, cause atm meshing is taking 90% of the render time

                    {//Up (y+)
                        var dir = Direction.UP.getId();
                        if ((buildMask & (1 << dir)) != 0) {
                            long up = Mapper.AIR;
                            if (y < 31) {
                                up = data[WorldSection.getIndex(x, y + 1, z)];
                            } else if (((buildMask >> (6 + dir)) & 1) == 0) {//This is to check with the build flags if it should build with respect to the neighboring chunk section
                                var connectedData = localConnections[dir];
                                if (connectedData == null) {
                                    var connectedSection = this.world.acquire(section.lvl, section.x, section.y + 1, section.z);
                                    connectedData = localConnections[dir] = this.connectedSectionCaches[dir];
                                    connectedSection.copyDataTo(connectedData);
                                    connectedSection.release();
                                }
                                up = connectedData[WorldSection.getIndex(x, 0, z)];
                            }


                            if (Mapper.shouldRenderFace(dir, self, up)) {
                                this.meshers[dir].put(x, z, (self&~(0xFFL<<56))|(up&(0xFFL<<56)));
                            }
                        }
                    }
                    /*
                    long up = -1;
                    if (y < 31) {
                        up = data[WorldSection.getIndex(x, y + 1, z)];
                        if (!Mapper.isTranslucent(up)) {
                            continue;
                        }
                    }
                    if (y == 31 && ((buildMask>>(6+dirId))&1) == 0) {
                            //Load and copy the data into a local cache, TODO: optimize so its not doing billion of copies
                            if (connectedData == null) {
                                var connectedSection = this.world.acquire(section.lvl, section.x, section.y + 1, section.z);
                                connectedSection.copyDataTo(this.connectedSectionCache);
                                connectedData = this.connectedSectionCache;
                                connectedSection.release();
                            }
                            up = connectedData[WorldSection.getIndex(x, 0, z)];
                            if (!Mapper.isTranslucent(up)) {
                                continue;
                            }
                        }
                        this.mesher.put(x, z, (self&~(0xFFL<<56))|(up&(0xFFL<<56)));
                    }

                }

                var count = this.mesher.process();
                var array = this.mesher.getArray();
                for (int i = 0; i < count; i++) {
                    var quad = array[i];
                    this.outData.add(QuadFormat.encode(null, this.mesher.getDataFromQuad(quad), 1, y, quad));
                */
                }
            }
        }

        if (this.outData.isEmpty()) {
            return new BuiltSectionGeometry(section.getKey(), null, null);
        }

        var output = new MemoryBuffer(this.outData.size()*8L);
        for (int i = 0; i < this.outData.size(); i++) {
            MemoryUtil.memPutLong(output.address + i * 8L, this.outData.getLong(i));
        }

        this.outData.clear();
        return new BuiltSectionGeometry(section.getKey(), output, null);
    }
}
