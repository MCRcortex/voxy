package me.cortex.voxy.client.compat;

import net.fabricmc.loader.api.FabricLoader;

public class SodiumExtra {
    public static final boolean HAS_SODIUM_EXTRA = FabricLoader.getInstance().isModLoaded("sodium-extra");
    public static boolean useSodiumExtraCulling() {
        return HAS_SODIUM_EXTRA;
    }
}
