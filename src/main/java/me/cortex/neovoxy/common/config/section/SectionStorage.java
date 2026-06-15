package me.cortex.neovoxy.common.config.section;

import me.cortex.neovoxy.common.config.IMappingStorage;
import me.cortex.neovoxy.common.world.WorldSection;

public abstract class SectionStorage implements IMappingStorage {
    public abstract int loadSection(WorldSection into);

    public abstract void saveSection(WorldSection section);
}
