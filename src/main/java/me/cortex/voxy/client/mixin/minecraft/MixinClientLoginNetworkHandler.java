package me.cortex.voxy.client.mixin.minecraft;

import me.cortex.voxy.client.LoadException;
import me.cortex.voxy.client.VoxyClientInstance;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.*;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.time.Duration;
import java.util.function.Consumer;

@Mixin(ClientLoginNetworkHandler.class)
public class MixinClientLoginNetworkHandler {
    @Inject(method = "<init>", at = @At(value = "TAIL"))
    private void voxy$init(ClientConnection connection, MinecraftClient client, ServerInfo serverInfo, Screen parentScreen, boolean newWorld, Duration worldLoadTime, Consumer statusConsumer, CookieStorage cookieStorage, CallbackInfo ci) {
        if (VoxyCommon.isAvailable()) {
            VoxyClientInstance.isInGame = true;
            if (VoxyConfig.CONFIG.enabled) {
                VoxyCommon.createInstance();
            }
        }
    }
}
