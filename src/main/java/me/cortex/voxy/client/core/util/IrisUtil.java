package me.cortex.voxy.client.core.util;

import net.irisshaders.iris.shadows.ShadowRenderer;
import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.Unique;

public class IrisUtil {
    private static final boolean IRIS_INSTALLED = FabricLoader.getInstance().isModLoaded("iris");


    private static boolean irisShadowActive0() {
        return ShadowRenderer.ACTIVE;
    }

    public static boolean irisShadowActive() {
        return IRIS_INSTALLED && irisShadowActive0();
    }
}
