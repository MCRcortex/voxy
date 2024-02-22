package me.cortex.voxy.common.storage.config;

import me.cortex.voxy.common.config.Serialization;
import me.cortex.voxy.common.storage.StorageBackend;

import java.util.ArrayList;
import java.util.List;

public abstract class StorageConfig {
    static {
        Serialization.CONFIG_TYPES.add(StorageConfig.class);
    }

    public abstract StorageBackend build(ConfigBuildCtx ctx);

    public List<StorageConfig> getChildStorageConfigs() {
        return List.of();
    }

    public final List<StorageConfig> collectStorageConfigs() {
        List<StorageConfig> configs = new ArrayList<>();
        configs.add(this);
        for (var child : this.getChildStorageConfigs()) {
            configs.addAll(child.collectStorageConfigs());
        }
        return configs;
    }
}
