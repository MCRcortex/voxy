package me.cortex.voxy.client.mixin.minecraft;

import me.cortex.voxy.client.VoxyClientInstance;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MixinMinecraftClient {
    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;Z)V", at = @At("TAIL"))
    private void voxy$injectWorldClose(CallbackInfo ci) {
        if (VoxyCommon.isAvailable()) {
            VoxyCommon.shutdownInstance();
            VoxyClientInstance.isInGame = false;
        }
    }

    /*
    @Inject(method = "joinWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;setWorld(Lnet/minecraft/client/world/ClientWorld;)V", shift = At.Shift.BEFORE))
    private void voxy$injectInitialization(ClientWorld world, DownloadingTerrainScreen.WorldEntryReason worldEntryReason, CallbackInfo ci) {
        if (VoxyConfig.CONFIG.enabled) {
            VoxyCommon.createInstance();
        }
    }*/
}
