package me.cortex.voxy.server;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl;

public class VoxyServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
    }
}

//The way the initial handshake works
// client joins
// if client supports S2CHello -> send S2CHello (containing mod version info, etc)
// client responds with C2SHello (containing mod version info, etc) -> Server registers all packets relating to voxy for player, sends ACK S2CHello
// on ACK S2CHello, client registers all packet handlers for voxy
// server then sends ConfigSync -> client initalizes the config it will use while on the server
//
