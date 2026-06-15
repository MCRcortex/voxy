package me.cortex.neovoxy.client.core.rendering;

import me.cortex.neovoxy.common.world.WorldEngine;

public interface ISectionWatcher {
    default boolean watch(int lvl, int x, int y, int z, int types) {
        return this.watch(WorldEngine.getWorldSectionId(lvl, x, y, z), types);
    }

    boolean watch(long position, int types);

    default boolean unwatch(int lvl, int x, int y, int z, int types) {
        return this.unwatch(WorldEngine.getWorldSectionId(lvl, x, y, z), types);
    }

    boolean unwatch(long position, int types);

    default int get(int lvl, int x, int y, int z) {
        return this.get(WorldEngine.getWorldSectionId(lvl, x, y, z));
    }

    int get(long position);
}
