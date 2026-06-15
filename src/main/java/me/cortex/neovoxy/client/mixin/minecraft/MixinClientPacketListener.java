package me.cortex.neovoxy.client.mixin.minecraft;

import me.cortex.neovoxy.client.NeoVoxyClientInstance;
import me.cortex.neovoxy.client.config.NeoVoxyConfig;
import me.cortex.neovoxy.commonImpl.NeoVoxyCommon;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {
    @Inject(method = "handleLogin", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;commonPlayerSpawnInfo()Lnet/minecraft/network/protocol/game/CommonPlayerSpawnInfo;"))
    private void neovoxy$init(ClientboundLoginPacket packet, CallbackInfo ci) {
        if (NeoVoxyCommon.isAvailable() && !NeoVoxyClientInstance.isInGame) {
            NeoVoxyClientInstance.isInGame = true;
            if (NeoVoxyConfig.CONFIG.enabled) {
                if (NeoVoxyCommon.getInstance() != null) {
                    NeoVoxyCommon.shutdownInstance();
                }
                NeoVoxyCommon.createInstance();
            }
        }
    }
}
