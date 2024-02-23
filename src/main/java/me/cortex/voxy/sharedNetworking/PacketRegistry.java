package me.cortex.voxy.sharedNetworking;

import me.cortex.voxy.common.util.ClassFinder;
import me.cortex.voxy.sharedNetworking.c2s.C2SHello;
import me.cortex.voxy.sharedNetworking.s2c.S2CHello;
import me.cortex.voxy.sharedNetworking.s2c.SectionUpdatePacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

public class PacketRegistry {
    public static void registerPackets() {
        ServerPlayNetworking.registerGlobalReceiver(C2SHello.TYPE, (packet, player, responseSender) -> {

        });
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            ClientPlayNetworking.registerGlobalReceiver(S2CHello.TYPE, (packet, player, responseSender) -> {

            });
            ClientPlayNetworking.registerGlobalReceiver(SectionUpdatePacket.TYPE, (packet, player, responseSender) -> {packet.handle(player);});
        }
    }
}
