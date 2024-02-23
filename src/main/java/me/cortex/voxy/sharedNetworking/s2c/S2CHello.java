package me.cortex.voxy.sharedNetworking.s2c;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

//Hello to client
public class S2CHello implements FabricPacket {
    public static final PacketType<S2CHello> TYPE = PacketType.create(new Identifier("voxy", "s2chello"), S2CHello::new);
    @Override
    public PacketType<?> getType() {return TYPE;}

    private S2CHello(PacketByteBuf buf) {

    }

    @Override
    public void write(PacketByteBuf buf) {

    }
}
