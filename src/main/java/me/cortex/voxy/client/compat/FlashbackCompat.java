package me.cortex.voxy.client.compat;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public class FlashbackCompat {
    public static final boolean FLASHBACK_INSTALLED = FabricLoader.getInstance().isModLoaded("flashback");

    public static Path getReplayStoragePath() {
        if (!FLASHBACK_INSTALLED) {
            return null;
        }
        // TODO: re-enable when flashback updates for 26.2
        return null;
    }
}
