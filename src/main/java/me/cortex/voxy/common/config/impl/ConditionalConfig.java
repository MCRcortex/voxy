package me.cortex.voxy.common.config.impl;

import com.google.gson.annotations.SerializedName;
import me.cortex.voxy.common.config.AbstractConfig;
import me.cortex.voxy.common.config.ConfigBuildCtx;

import java.util.List;
import java.util.Map;

public class ConditionalConfig <T> extends AbstractConfig<T> {
    static final class ConditionalConfigEntry <T> {
        Map<String, String> conditions;
        AbstractConfig<T> delegate;

        public boolean meetsConditions(ConfigBuildCtx ctx) {
            for (var condition : this.conditions.entrySet()) {
                if (!condition.getValue().equals(ctx.resolveString(condition.getKey()))) {
                    return false;
                }
            }
            return true;
        }
    }

    List<ConditionalConfigEntry<T>> configs;

    @SerializedName("default")
    AbstractConfig<T> defaultDelegate;

    public T build(ConfigBuildCtx ctx) {
        for (var config : this.configs) {
            if (config.meetsConditions(ctx)) {
                return config.delegate.build(ctx);
            }
        }
        return this.defaultDelegate.build(ctx);
    }

    public static String getConfigTypeName() {
        return "ConditionalConfig";
    }
}
