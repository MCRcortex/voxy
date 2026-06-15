package me.cortex.neovoxy.common.config.compressors;

import me.cortex.neovoxy.common.config.ConfigBuildCtx;
import me.cortex.neovoxy.common.config.Serialization;

public abstract class CompressorConfig {
    static {
        Serialization.CONFIG_TYPES.add(CompressorConfig.class);
    }

    public abstract StorageCompressor build(ConfigBuildCtx ctx);
}
