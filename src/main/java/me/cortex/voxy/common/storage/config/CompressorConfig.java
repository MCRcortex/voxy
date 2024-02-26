package me.cortex.voxy.common.storage.config;

import me.cortex.voxy.common.config.AbstractConfig;
import me.cortex.voxy.common.config.ConfigBuildCtx;
import me.cortex.voxy.common.config.Serialization;
import me.cortex.voxy.common.storage.StorageCompressor;

public abstract class CompressorConfig extends AbstractConfig<StorageCompressor> {
    static {
        Serialization.register(CompressorConfig.class);
    }

    public abstract StorageCompressor build(ConfigBuildCtx ctx);
}
