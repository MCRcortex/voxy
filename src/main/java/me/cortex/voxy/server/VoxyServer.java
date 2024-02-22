package me.cortex.voxy.server;

import me.cortex.voxy.sharedNetworking.Common;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl;

public class VoxyServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        ServerPlayNetworking.registerGlobalReceiver(Common.HELLO, (a,b,c,d,e) -> {

        });
    }
}
