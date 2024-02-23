package me.cortex.voxy.sharedNetworking.s2c;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

//Single section update
public class SectionUpdatePacket implements FabricPacket {
    public static final PacketType<SectionUpdatePacket> TYPE = PacketType.create(new Identifier("voxy", "sectionUpdate"), SectionUpdatePacket::new);
    @Override
    public PacketType<?> getType() {return TYPE;}

    private SectionUpdatePacket(PacketByteBuf buf) {

    }

    @Override
    public void write(PacketByteBuf buf) {

    }

    public void handle(ClientPlayerEntity player) {
    }
}
