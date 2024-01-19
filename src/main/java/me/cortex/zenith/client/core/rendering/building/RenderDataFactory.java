package me.cortex.zenith.client.core.rendering.building;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import me.cortex.zenith.common.util.MemoryBuffer;
import me.cortex.zenith.client.core.util.Mesher2D;
import me.cortex.zenith.common.world.WorldEngine;
import me.cortex.zenith.common.world.WorldSection;
import me.cortex.zenith.common.world.other.Mapper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Direction;
import org.lwjgl.system.MemoryUtil;


public class RenderDataFactory {
    private final Mesher2D mesher = new Mesher2D(5,15);//15
    private final LongArrayList outData = new LongArrayList(1000);
    private final WorldEngine world;
    private final long[] sectionCache = new long[32*32*32];
    private final long[] connectedSectionCache = new long[32*32*32];
    private final QuadEncoder encoder;
    public RenderDataFactory(WorldEngine world) {
        this.world = world;
        this.encoder = new QuadEncoder(world.getMapper(), MinecraftClient.getInstance().getBlockColors(), MinecraftClient.getInstance().world);
    }


    //TODO: MAKE a render cache that caches each WorldSection directional face generation, cause then can just pull that directly
    // instead of needing to regen the entire thing


    //section is already acquired and gets released by the parent

    //buildMask in the lower 6 bits contains the faces to build, the next 6 bits are whether the edge face builds against
    // its neigbor or not (0 if it does 1 if it doesnt (0 is default behavior))
    public BuiltSectionGeometry generateMesh(WorldSection section, int buildMask) {
        //TODO: to speed it up more, check like section.isEmpty() and stuff like that, have masks for if a slice/layer is entirly air etc

        //TODO: instead of having it check its neighbors with the same lod level, compare against 1 level lower, this will prevent cracks and seams from
        // appearing between lods


        //if (section.definitelyEmpty()) {//Fast path if its known the entire chunk is empty
        //    return new BuiltSectionGeometry(section.getKey(), null, null);
        //}

        section.copyDataTo(this.sectionCache);
        var data = this.sectionCache;

        long[] connectedData = null;
        int dirId = Direction.UP.getId();
        if ((buildMask&(1<<dirId))!=0) {
            for (int y = 0; y < 32; y++) {
                this.mesher.reset();

                for (int z = 0; z < 32; z++) {
                    for (int x = 0; x < 32; x++) {
                        var self = data[WorldSection.getIndex(x, y, z)];
                        if (Mapper.isAir(self)) {
                            continue;
                        }
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
                        //Recodes the id to include the correct lighting
                        this.mesher.put(x, z, (self&~(0xFFL<<56))|(up&(0xFFL<<56)));
                    }
                }

                var count = this.mesher.process();
                var array = this.mesher.getArray();
                for (int i = 0; i < count; i++) {
                    var quad = array[i];
                    this.outData.add(this.encoder.encode(this.mesher.getDataFromQuad(quad), 1, y, quad));
                }
            }
            connectedData = null;
        }

        dirId = Direction.EAST.getId();
        if ((buildMask&(1<<dirId))!=0) {
            for (int x = 0; x < 32; x++) {
                this.mesher.reset();

                for (int y = 0; y < 32; y++) {
                    for (int z = 0; z < 32; z++) {
                        var self = data[WorldSection.getIndex(x, y, z)];
                        if (Mapper.isAir(self)) {
                            continue;
                        }
                        long up = -1;
                        if (x < 31) {
                            up = data[WorldSection.getIndex(x + 1, y, z)];
                            if (!Mapper.isTranslucent(up)) {
                                continue;
                            }
                        }
                        if (x == 31 && ((buildMask>>(6+dirId))&1) == 0) {
                            //Load and copy the data into a local cache, TODO: optimize so its not doing billion of copies
                            if (connectedData == null) {
                                var connectedSection = this.world.acquire(section.lvl, section.x + 1, section.y, section.z);
                                connectedSection.copyDataTo(this.connectedSectionCache);
                                connectedData = this.connectedSectionCache;
                                connectedSection.release();
                            }
                            up = connectedData[WorldSection.getIndex(0, y, z)];
                            if (!Mapper.isTranslucent(up)) {
                                continue;
                            }
                        }
                        this.mesher.put(y, z, (self&~(0xFFL<<56))|(up&(0xFFL<<56)));
                    }
                }

                var count = this.mesher.process();
                var array = this.mesher.getArray();
                for (int i = 0; i < count; i++) {
                    var quad = array[i];
                    this.outData.add(this.encoder.encode(this.mesher.getDataFromQuad(quad), 5, x, quad));
                }
            }
            connectedData = null;
        }

        dirId = Direction.SOUTH.getId();
        if ((buildMask&(1<<dirId))!=0) {
            for (int z = 0; z < 32; z++) {
                this.mesher.reset();

                for (int x = 0; x < 32; x++) {
                    for (int y = 0; y < 32; y++) {
                        var self = data[WorldSection.getIndex(x, y, z)];
                        if (Mapper.isAir(self)) {
                            continue;
                        }
                        long up = -1;
                        if (z < 31) {
                            up = data[WorldSection.getIndex(x, y, z + 1)];
                            if (!Mapper.isTranslucent(up)) {
                                continue;
                            }
                        }
                        if (z == 31 && ((buildMask>>(6+dirId))&1) == 0) {
                            //Load and copy the data into a local cache, TODO: optimize so its not doing billion of copies
                            if (connectedData == null) {
                                var connectedSection = this.world.acquire(section.lvl, section.x, section.y, section.z + 1);
                                connectedSection.copyDataTo(this.connectedSectionCache);
                                connectedData = this.connectedSectionCache;
                                connectedSection.release();
                            }
                            up = connectedData[WorldSection.getIndex(x, y, 0)];
                            if (!Mapper.isTranslucent(up)) {
                                continue;
                            }
                        }
                        this.mesher.put(x, y, (self&~(0xFFL<<56))|(up&(0xFFL<<56)));
                    }
                }

                var count = this.mesher.process();
                var array = this.mesher.getArray();
                for (int i = 0; i < count; i++) {
                    var quad = array[i];
                    this.outData.add(this.encoder.encode(this.mesher.getDataFromQuad(quad), 3, z, quad));
                }
            }
            connectedData = null;
        }

        dirId = Direction.WEST.getId();
        if ((buildMask&(1<<dirId))!=0) {
            for (int x = 31; x != -1; x--) {
                this.mesher.reset();

                for (int y = 0; y < 32; y++) {
                    for (int z = 0; z < 32; z++) {
                        var self = data[WorldSection.getIndex(x, y, z)];
                        if (Mapper.isAir(self)) {
                            continue;
                        }
                        long up = -1;
                        if (x != 0) {
                            up = data[WorldSection.getIndex(x - 1, y, z)];
                            if (!Mapper.isTranslucent(up)) {
                                continue;
                            }
                        }
                        if (x == 0 && ((buildMask>>(6+dirId))&1) == 0) {
                            //Load and copy the data into a local cache, TODO: optimize so its not doing billion of copies
                            if (connectedData == null) {
                                var connectedSection = this.world.acquire(section.lvl, section.x - 1, section.y, section.z);
                                connectedSection.copyDataTo(this.connectedSectionCache);
                                connectedData = this.connectedSectionCache;
                                connectedSection.release();
                            }
                            up = connectedData[WorldSection.getIndex(31, y, z)];
                            if (!Mapper.isTranslucent(up)) {
                                continue;
                            }
                        }
                        this.mesher.put(y, z, (self&~(0xFFL<<56))|(up&(0xFFL<<56)));
                    }
                }

                var count = this.mesher.process();
                var array = this.mesher.getArray();
                for (int i = 0; i < count; i++) {
                    var quad = array[i];
                    this.outData.add(this.encoder.encode(this.mesher.getDataFromQuad(quad), 4, x, quad));
                }
            }
            connectedData = null;
        }

        dirId = Direction.NORTH.getId();
        if ((buildMask&(1<<dirId))!=0) {
            for (int z = 31; z != -1; z--) {
                this.mesher.reset();

                for (int x = 0; x < 32; x++) {
                    for (int y = 0; y < 32; y++) {
                        var self = data[WorldSection.getIndex(x, y, z)];
                        if (Mapper.isAir(self)) {
                            continue;
                        }
                        long up = -1;
                        if (z != 0) {
                            up = data[WorldSection.getIndex(x, y, z - 1)];
                            if (!Mapper.isTranslucent(up)) {
                                continue;
                            }
                        }
                        if (z == 0 && ((buildMask>>(6+dirId))&1) == 0) {
                            //Load and copy the data into a local cache, TODO: optimize so its not doing billion of copies
                            if (connectedData == null) {
                                var connectedSection = this.world.acquire(section.lvl, section.x, section.y, section.z - 1);
                                connectedSection.copyDataTo(this.connectedSectionCache);
                                connectedData = this.connectedSectionCache;
                                connectedSection.release();
                            }
                            up = connectedData[WorldSection.getIndex(x, y, 31)];
                            if (!Mapper.isTranslucent(up)) {
                                continue;
                            }
                        }
                        this.mesher.put(x, y, (self&~(0xFFL<<56))|(up&(0xFFL<<56)));
                    }
                }

                var count = this.mesher.process();
                var array = this.mesher.getArray();
                for (int i = 0; i < count; i++) {
                    var quad = array[i];
                    this.outData.add(this.encoder.encode(this.mesher.getDataFromQuad(quad), 2, z, quad));
                }
            }
            connectedData = null;
        }

        dirId = Direction.DOWN.getId();
        if ((buildMask&(1<<dirId))!=0) {
            for (int y = 31; y != -1; y--) {
                this.mesher.reset();

                for (int x = 0; x < 32; x++) {
                    for (int z = 0; z < 32; z++) {
                        var self = data[WorldSection.getIndex(x, y, z)];
                        if (Mapper.isAir(self)) {
                            continue;
                        }
                        long up = -1;
                        if (y != 0) {
                            up = data[WorldSection.getIndex(x, y - 1, z)];
                            if (!Mapper.isTranslucent(up)) {
                                continue;
                            }
                        }
                        if (y == 0 && ((buildMask>>(6+dirId))&1) == 0) {
                            //Load and copy the data into a local cache, TODO: optimize so its not doing billion of copies
                            if (connectedData == null) {
                                var connectedSection = this.world.acquire(section.lvl, section.x, section.y - 1, section.z);
                                connectedSection.copyDataTo(this.connectedSectionCache);
                                connectedData = this.connectedSectionCache;
                                connectedSection.release();
                            }
                            up = connectedData[WorldSection.getIndex(x, 31, z)];
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
                    this.outData.add(this.encoder.encode(this.mesher.getDataFromQuad(quad), 0, y, quad));
                }
            }
            connectedData = null;
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
