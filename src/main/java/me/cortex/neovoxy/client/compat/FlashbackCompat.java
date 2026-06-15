package me.cortex.neovoxy.client.compat;

// TODO: Re-enable Flashback integration when NeoForge 1.21.1 version available
// import com.moulberry.flashback.Flashback;
// import com.moulberry.flashback.playback.ReplayServer;
// import com.moulberry.flashback.record.FlashbackMeta;
import me.cortex.neovoxy.common.Logger;
import me.cortex.neovoxy.common.config.section.SectionStorageConfig;
// Flashback integration is disabled until a native NeoForge release is available.
// import net.neoforged.fml.ModList;

import java.nio.file.Path;

public class FlashbackCompat {
    // Disabled for NeoForge 1.21.1 port - Flashback not available
    public static final boolean FLASHBACK_INSTALLED = false;

    public static Path getReplayStoragePath() {
        // Stubbed out - Flashback integration disabled for NeoForge port
        return null;
        /*
        if (!FLASHBACK_INSTALLED) {
            return null;
        }
        return getReplayStoragePath0();
        */
    }

    /*
    private static Path getReplayStoragePath0() {
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer != null) {
            FlashbackMeta meta = replayServer.getMetadata();
            if (meta != null) {
                var path = ((IFlashbackMeta)meta).getNeoVoxyPath();
                if (path != null) {
                    Logger.info("Flashback replay server exists and meta exists");
                    if (path.exists()) {
                        Logger.info("Flashback neovoxy path exists in filesystem, using this as lod data source");
                        return path.toPath();
                    } else {
                        Logger.warn("Flashback meta had neovoxy path saved but path doesnt exist");
                    }
                }
            }
        }
        return null;
    }
    */
}
