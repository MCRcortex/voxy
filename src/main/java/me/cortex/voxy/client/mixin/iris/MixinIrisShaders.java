package me.cortex.voxy.client.mixin.iris;

import me.cortex.voxy.client.core.IGetVoxelCore;
import net.irisshaders.iris.config.IrisConfig;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IrisConfig.class, remap = false)
public class MixinIrisShaders {

    @Inject(method = "setShadersEnabled", at = @At("RETURN"))
    private void injectRefresh(boolean enabled, CallbackInfo ci) {
        var world = (IGetVoxelCore) MinecraftClient.getInstance().worldRenderer;
        if (world != null && !enabled) {
            world.reloadVoxelCore();
        }
    }
}
