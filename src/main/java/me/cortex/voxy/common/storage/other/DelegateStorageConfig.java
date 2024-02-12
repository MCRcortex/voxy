package me.cortex.voxy.common.storage.other;

import me.cortex.voxy.common.storage.config.StorageConfig;

import java.util.List;

public abstract class DelegateStorageConfig extends StorageConfig {
    public StorageConfig delegate;

    @Override
    public List<StorageConfig> getChildStorageConfigs() {
        return List.of(this.delegate);
    }
}
