package me.cortex.neovoxy.common.config.section;

import me.cortex.neovoxy.common.config.ConfigBuildCtx;
import me.cortex.neovoxy.common.config.Serialization;

public abstract class SectionStorageConfig {
    static {
        Serialization.CONFIG_TYPES.add(SectionStorageConfig.class);
    }

    public abstract SectionStorage build(ConfigBuildCtx ctx);
}
