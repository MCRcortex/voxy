package me.cortex.voxy.common.storage.other;

import me.cortex.voxy.common.storage.StorageBackend;
import me.cortex.voxy.common.storage.config.ConfigBuildCtx;

//Very simple config that adds a path to the config builder
public class BasicPathInsertionConfig extends DelegateStorageConfig {
    public String path = "";

    @Override
    public StorageBackend build(ConfigBuildCtx ctx) {
        ctx.pushPath(this.path);
        var storage = this.delegate.build(ctx);
        ctx.popPath();
        return storage;
    }

    public static String getConfigTypeName() {
        return "BasicPathConfig";
    }
}
