package me.cortex.voxy.client.compat;

import java.nio.file.Path;

/**
 * Flashback is currently distributed for Fabric only, so no replay storage
 * integration is available in the NeoForge build.
 */
public final class FlashbackCompat {
    public static final boolean FLASHBACK_INSTALLED = false;

    private FlashbackCompat() {
    }

    public static Path getReplayStoragePath() {
        return null;
    }
}
