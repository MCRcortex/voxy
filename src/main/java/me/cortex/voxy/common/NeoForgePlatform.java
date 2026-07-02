package me.cortex.voxy.common;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;

public final class NeoForgePlatform {
    private NeoForgePlatform() {}

    //safe durring early mixin bootstrap when ModList isnt built yet
    public static boolean isModLoaded(String modId) {
        try {
            var ml = ModList.get();
            if (ml != null) {
                return ml.isLoaded(modId);
            }
            var lml = LoadingModList.get();
            return lml != null && lml.getModFileById(modId) != null;
        } catch (Throwable t) {
            return false;
        }
    }
}
