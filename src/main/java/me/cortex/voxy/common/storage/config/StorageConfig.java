package me.cortex.voxy.common.storage.config;

import me.cortex.voxy.common.config.AbstractConfig;
import me.cortex.voxy.common.config.ConfigBuildCtx;
import me.cortex.voxy.common.config.Serialization;
import me.cortex.voxy.common.storage.StorageBackend;

import java.util.ArrayList;
import java.util.List;

public abstract class StorageConfig extends AbstractConfig<StorageBackend> {
    static {
        Serialization.register(StorageConfig.class);
    }

    public abstract StorageBackend build(ConfigBuildCtx ctx);
}
