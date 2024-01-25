package me.cortex.zenith.client.core.rendering.building;

import me.cortex.zenith.common.world.WorldEngine;
import me.cortex.zenith.common.world.WorldSection;
import net.minecraft.client.MinecraftClient;


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

        return new BuiltSection(section.getKey(), null, null);
    }

}
