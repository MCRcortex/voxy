package me.cortex.voxy.server.world;

import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.world.chunk.WorldChunk;

//Per world/dim/level instance
public class VoxyWorld {
    private final WorldEngine engine;

    public VoxyWorld(WorldEngine engine) {
        this.engine = engine;
    }

    public void enqueueIngest(WorldChunk chunk) {

    }

    public void shutdown() {

    }
}
