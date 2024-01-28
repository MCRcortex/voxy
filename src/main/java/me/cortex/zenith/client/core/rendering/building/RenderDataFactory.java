package me.cortex.zenith.client.core.rendering.building;

import me.cortex.zenith.common.util.MemoryBuffer;
import me.cortex.zenith.common.world.WorldEngine;
import me.cortex.zenith.common.world.WorldSection;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.system.MemoryUtil;


public class RenderDataFactory {
    private final WorldEngine world;
    private final QuadEncoder encoder;
    private final long[] sectionCache = new long[32*32*32];
    private final long[] connectedSectionCache = new long[32*32*32];
    public RenderDataFactory(WorldEngine world) {
        this.world = world;
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

        var buff = new MemoryBuffer(8*8);
        MemoryUtil.memPutLong(buff.address,        encodeRaw(2, 0,0,0,0,0,515,0, 0));//92
        MemoryUtil.memPutLong(buff.address+8,  encodeRaw(3, 0,0,0,0,0,515,0, 0));//92
        MemoryUtil.memPutLong(buff.address+16, encodeRaw(4, 0,2,0,0,0,515,0, 0));//92
        MemoryUtil.memPutLong(buff.address+24, encodeRaw(5, 0,2,0,0,0,515,0, 0));//92
        MemoryUtil.memPutLong(buff.address+32, encodeRaw(2, 0,0,0,0,1,515,0, 0));//92
        MemoryUtil.memPutLong(buff.address+40, encodeRaw(3, 0,0,0,0,1,515,0, 0));//92
        MemoryUtil.memPutLong(buff.address+48, encodeRaw(2, 0,0,0,0,2,515,0, 0));//92
        MemoryUtil.memPutLong(buff.address+56, encodeRaw(3, 0,0,0,0,2,515,0, 0));//92

        return new BuiltSection(section.getKey(), new BuiltSectionGeometry(buff, new short[0]), null);
    }


    private static long encodeRaw(int face, int width, int height, int x, int y, int z, int blockId, int biomeId, int lightId) {
        return ((long)face) | (((long) width)<<3) | (((long) height)<<7) | (((long) z)<<11) | (((long) y)<<16) | (((long) x)<<21) | (((long) blockId)<<26) | (((long) biomeId)<<46) | (((long) lightId)<<55);
    }

}
