// package me.cortex.voxy.client.mixin.minecraft;

// import me.cortex.voxy.client.LoadException;
// import net.minecraft.client.network.ClientCommonNetworkHandler;
// import net.minecraft.network.packet.Packet;
// import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
// import org.spongepowered.asm.mixin.Mixin;
// import org.spongepowered.asm.mixin.injection.At;
// import org.spongepowered.asm.mixin.injection.Inject;
// import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// @Mixin(ClientCommonNetworkHandler.class)
// public class MixinClientCommonNetworkHandler {
//     @Inject(method = "onPacketException", at = @At("HEAD"), cancellable = true)
//     private void handleDisconnectAsCrash(Packet<?> packet, Exception exception, CallbackInfo ci) {
//         if (packet instanceof GameJoinS2CPacket) {
//             ci.cancel();
//             throw new LoadException("Force crashing due to exception during on game join", exception);
//         }
//     }
// }
