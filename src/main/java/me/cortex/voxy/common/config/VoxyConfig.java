package me.cortex.voxy.common.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * NeoForge common (server+client) configuration for Voxy.
 * Loaded from {@code config/voxy-common.toml}.
 */
public final class VoxyConfig {

    public static final VoxyConfig INSTANCE;
    public static final ModConfigSpec SPEC;

    static {
        Pair<VoxyConfig, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(VoxyConfig::new);
        INSTANCE = specPair.getLeft();
        SPEC     = specPair.getRight();
    }

    // -------------------------------------------------------------------------
    // Server settings
    // -------------------------------------------------------------------------

    /** Maximum LOD level generated on the server. 0 = finest only; 4 = up to 16× coarsening. */
    public final ModConfigSpec.IntValue serverMaxLodLevel;

    /** Number of chunks to voxelize per tick on the server worker thread. */
    public final ModConfigSpec.IntValue serverVoxelizeRatePerTick;

    /** Maximum number of section-data packets queued for a single client. */
    public final ModConfigSpec.IntValue serverMaxTransferQueuePerClient;

    // -------------------------------------------------------------------------
    // Client settings
    // -------------------------------------------------------------------------

    /** Client-side LOD render radius in sections at LOD 0. */
    public final ModConfigSpec.IntValue clientLodRadius;

    /** Whether to show a HUD overlay with LOD sync statistics. */
    public final ModConfigSpec.BooleanValue clientShowSyncHud;

    // -------------------------------------------------------------------------

    private VoxyConfig(ModConfigSpec.Builder builder) {
        builder.push("server");

        serverMaxLodLevel = builder
                .comment("Maximum LOD level the server generates. Higher = more data pre-generated.",
                         "0 = LOD 0 only (finest). 4 = levels 0-4.")
                .defineInRange("maxLodLevel", 4, 0, 7);

        serverVoxelizeRatePerTick = builder
                .comment("How many chunks are voxelized per server tick. Lower = less CPU impact.")
                .defineInRange("voxelizeRatePerTick", 8, 1, 64);

        serverMaxTransferQueuePerClient = builder
                .comment("Maximum pending LOD section packets queued per connected client.",
                         "Reduces memory use when clients are on slow connections.")
                .defineInRange("maxTransferQueuePerClient", 2048, 64, 65536);

        builder.pop().push("client");

        clientLodRadius = builder
                .comment("LOD render radius in sections (at LOD 0). Each section = 1 chunk.")
                .defineInRange("lodRadius", 64, 8, 512);

        clientShowSyncHud = builder
                .comment("Display LOD sync progress in the HUD debug overlay.")
                .define("showSyncHud", true);

        builder.pop();
    }
}
