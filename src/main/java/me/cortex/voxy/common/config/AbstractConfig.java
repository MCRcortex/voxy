package me.cortex.voxy.common.config;


public abstract class AbstractConfig<T> {
    static {
        Serialization.register(AbstractConfig.class);
    }

    public abstract T build(ConfigBuildCtx buildCtx);
}
