package me.cortex.voxy.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S→C: delivers the serialized data for one LOD section.
 *
 * <p>Sent in response to {@link RequestSectionsPayload} and also pushed proactively
 * when the server updates a section while a client is connected (live update).
 *
 * <p>{@code data} is the raw bytes from {@link me.cortex.voxy.common.lod.LodSection#toBytes()}.
 */
public record LodSectionDataPayload(
        ResourceLocation dimension,
        long sectionKey,
        long hash,
        byte[] data
) implements CustomPacketPayload {

    public static final Type<LodSectionDataPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("voxy", "lod_section_data"));

    public static final StreamCodec<FriendlyByteBuf, LodSectionDataPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public LodSectionDataPayload decode(FriendlyByteBuf buf) {
                    ResourceLocation dim = ResourceLocation.STREAM_CODEC.decode(buf);
                    long key  = buf.readLong();
                    long hash = buf.readLong();
                    byte[] data = buf.readByteArray();
                    return new LodSectionDataPayload(dim, key, hash, data);
                }

                @Override
                public void encode(FriendlyByteBuf buf, LodSectionDataPayload payload) {
                    ResourceLocation.STREAM_CODEC.encode(buf, payload.dimension());
                    buf.writeLong(payload.sectionKey());
                    buf.writeLong(payload.hash());
                    buf.writeByteArray(payload.data());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
