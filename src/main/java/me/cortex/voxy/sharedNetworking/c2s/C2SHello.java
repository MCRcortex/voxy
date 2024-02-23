package me.cortex.voxy.sharedNetworking.c2s;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

//Hello to server
public class C2SHello implements FabricPacket {
    public static final PacketType<C2SHello> TYPE = PacketType.create(new Identifier("voxy", "c2shello"), C2SHello::new);
    @Override
    public PacketType<?> getType() {return TYPE;}

    private C2SHello(PacketByteBuf buf) {

    }

    @Override
    public void write(PacketByteBuf buf) {

    }

}
