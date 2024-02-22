package me.cortex.voxy.common.storage.config;

import me.cortex.voxy.common.config.Serialization;
import me.cortex.voxy.common.storage.StorageCompressor;

public abstract class CompressorConfig {
    static {
        Serialization.CONFIG_TYPES.add(CompressorConfig.class);
    }

    public abstract StorageCompressor build(ConfigBuildCtx ctx);
}
