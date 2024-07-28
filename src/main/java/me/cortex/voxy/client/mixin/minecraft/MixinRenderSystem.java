package me.cortex.voxy.client.mixin.minecraft;

import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
public class MixinRenderSystem {
    @Inject(method = {"assertOnRenderThread", "assertOnRenderThreadOrInit"}, at = @At("HEAD"), cancellable = true)
    private static void cancelAssert(CallbackInfo ci) {
        ci.cancel();
    }
}
