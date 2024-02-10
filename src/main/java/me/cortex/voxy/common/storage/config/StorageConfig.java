package me.cortex.voxy.common.storage.config;

import me.cortex.voxy.common.storage.StorageBackend;

public abstract class StorageConfig {
    static {
        Serialization.CONFIG_TYPES.add(StorageConfig.class);
    }

    public abstract StorageBackend build(ConfigBuildCtx ctx);
}
