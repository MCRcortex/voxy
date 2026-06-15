package me.cortex.neovoxy.common.config.storage.other;

import me.cortex.neovoxy.common.config.storage.StorageConfig;

import java.util.List;

public abstract class DelegateStorageConfig extends StorageConfig {
    public StorageConfig delegate;

    @Override
    public List<StorageConfig> getChildStorageConfigs() {
        return List.of(this.delegate);
    }
}
