package me.cortex.voxy.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S→C: notifies the client that a section has been removed from the server's storage
 * (e.g. the corresponding chunk was deleted or reset).
 */
public record SectionRemovePayload(
        ResourceLocation dimension,
        long sectionKey
) implements CustomPacketPayload {

    public static final Type<SectionRemovePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("voxy", "section_remove"));

    public static final StreamCodec<FriendlyByteBuf, SectionRemovePayload> CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, SectionRemovePayload::dimension,
                    StreamCodec.of(FriendlyByteBuf::writeLong, FriendlyByteBuf::readLong), SectionRemovePayload::sectionKey,
                    SectionRemovePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
