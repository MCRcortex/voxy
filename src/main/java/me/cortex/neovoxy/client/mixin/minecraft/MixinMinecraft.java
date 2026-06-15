package me.cortex.neovoxy.client.mixin.minecraft;

import me.cortex.neovoxy.client.NeoVoxyClientInstance;
import me.cortex.neovoxy.commonImpl.NeoVoxyCommon;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    @Inject(method = "disconnect", at = @At("TAIL"))
    private void neovoxy$injectWorldClose(CallbackInfo ci) {
        if (NeoVoxyCommon.isAvailable() && NeoVoxyClientInstance.isInGame) {
            NeoVoxyCommon.shutdownInstance();
            NeoVoxyClientInstance.isInGame = false;
        }
    }

    /*
    @Inject(method = "joinWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;setWorld(Lnet/minecraft/client/world/ClientWorld;)V", shift = At.Shift.BEFORE))
    private void neovoxy$injectInitialization(ClientWorld world, DownloadingTerrainScreen.WorldEntryReason worldEntryReason, CallbackInfo ci) {
        if (NeoVoxyConfig.CONFIG.enabled) {
            NeoVoxyCommon.createInstance();
        }
    }*/
}
