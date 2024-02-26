package me.cortex.voxy.common.storage.other;

import me.cortex.voxy.common.config.AbstractConfig;
import me.cortex.voxy.common.storage.StorageBackend;
import me.cortex.voxy.common.storage.StorageCompressor;
import me.cortex.voxy.common.storage.config.StorageConfig;

import java.util.List;

public abstract class DelegateStorageConfig extends StorageConfig {
    public AbstractConfig<StorageBackend> delegate;
}
