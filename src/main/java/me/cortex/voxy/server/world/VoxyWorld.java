package me.cortex.voxy.server.world;

import me.cortex.voxy.common.config.AbstractConfig;
import me.cortex.voxy.common.config.ConfigBuildCtx;
import me.cortex.voxy.common.storage.StorageBackend;
import me.cortex.voxy.common.storage.StorageCompressor;
import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.world.chunk.WorldChunk;

//Per world/dim/level instance
public class VoxyWorld {
    private final WorldEngine engine;

    private VoxyWorld(WorldEngine engine) {
        this.engine = engine;
    }

    public void enqueueIngest(WorldChunk chunk) {

    }

    public void shutdown() {

    }


    public static final class Config extends AbstractConfig<VoxyWorld> {
        public AbstractConfig<StorageBackend> storage;

        @Override
        public VoxyWorld build(ConfigBuildCtx ctx) {
            var storage = this.storage.build(ctx);
            return new VoxyWorld(new WorldEngine(storage, 0,0, 5));
        }

        public static String getConfigTypeName() {
            return "VoxyWorldServerConfig";
        }
    }
}
