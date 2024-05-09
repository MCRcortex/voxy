package me.cortex.voxy.client.mixin.minecraft;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MixinMinecraftClient {
    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resource/PeriodicNotificationManager;<init>(Lnet/minecraft/util/Identifier;Lit/unimi/dsi/fastutil/objects/Object2BooleanFunction;)V", shift = At.Shift.AFTER))
    private void injectRenderDoc(RunArgs args, CallbackInfo ci) {
        //System.load("C:\\Program Files\\RenderDoc\\renderdoc.dll");
    }
}
