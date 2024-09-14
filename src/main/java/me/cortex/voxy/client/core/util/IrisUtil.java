package me.cortex.voxy.client.core.util;

import me.cortex.voxy.client.core.IGetVoxelCore;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.apiimpl.IrisApiV0Impl;
import net.irisshaders.iris.config.IrisConfig;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

public class IrisUtil {
    private static final boolean IRIS_INSTALLED = FabricLoader.getInstance().isModLoaded("iris");


    private static boolean irisShadowActive0() {
        return ShadowRenderer.ACTIVE;
    }

    public static boolean irisShadowActive() {
        return IRIS_INSTALLED && irisShadowActive0();
    }

    public static boolean irisShadersEnabled() { return IRIS_INSTALLED && Iris.getIrisConfig().areShadersEnabled(); }
}
