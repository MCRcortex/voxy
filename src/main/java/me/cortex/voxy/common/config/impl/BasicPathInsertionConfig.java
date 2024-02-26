package me.cortex.voxy.common.config.impl;

import me.cortex.voxy.common.config.AbstractConfig;
import me.cortex.voxy.common.config.ConfigBuildCtx;

//Very simple config that adds a path to the config builder
public class BasicPathInsertionConfig <T> extends AbstractConfig<T> {
    AbstractConfig<T> delegate;
    String path = "";

    public T build(ConfigBuildCtx ctx) {
        ctx.pushPath(this.path);
        var storage = this.delegate.build(ctx);
        ctx.popPath();
        return storage;
    }

    public static String getConfigTypeName() {
        return "BasicPathConfig";
    }
}
