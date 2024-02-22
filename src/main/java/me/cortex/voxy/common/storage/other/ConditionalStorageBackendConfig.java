package me.cortex.voxy.common.storage.other;

import me.cortex.voxy.common.storage.StorageBackend;
import me.cortex.voxy.common.storage.config.ConfigBuildCtx;
import me.cortex.voxy.common.storage.config.StorageConfig;
import org.apache.commons.lang3.NotImplementedException;

//A conditional storage backend depending on build time config, this enables conditional backends depending on the
// dimension as an example
public class ConditionalStorageBackendConfig extends StorageConfig {
    @Override
    public StorageBackend build(ConfigBuildCtx ctx) {
        throw new NotImplementedException();
    }

    public static String getConfigTypeName() {
        return "ConditionalConfig";
    }
}
