package me.cortex.voxy.client.mixin.minecraft;

import me.cortex.voxy.client.LoadException;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public class MixinClientCommonPacketListenerImpl {
    @Inject(method = "onPacketError", at = @At("HEAD"), cancellable = true)
    private void handleDisconnectAsCrash(Packet<?> packet, Exception exception, CallbackInfo ci) {
        if (packet instanceof ClientboundLoginPacket) {
            ci.cancel();
            throw new LoadException("Force crashing due to exception during on game join", exception);
        }
    }
}
