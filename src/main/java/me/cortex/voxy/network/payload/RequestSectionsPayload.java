package me.cortex.voxy.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * C→S: client requests specific section keys it needs (missing or stale in its local cache).
 */
public record RequestSectionsPayload(
        ResourceLocation dimension,
        List<Long> sectionKeys
) implements CustomPacketPayload {

    public static final Type<RequestSectionsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("voxy", "request_sections"));

    public static final StreamCodec<FriendlyByteBuf, RequestSectionsPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public RequestSectionsPayload decode(FriendlyByteBuf buf) {
                    ResourceLocation dim = ResourceLocation.STREAM_CODEC.decode(buf);
                    int count = buf.readVarInt();
                    List<Long> keys = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) keys.add(buf.readLong());
                    return new RequestSectionsPayload(dim, keys);
                }

                @Override
                public void encode(FriendlyByteBuf buf, RequestSectionsPayload payload) {
                    ResourceLocation.STREAM_CODEC.encode(buf, payload.dimension());
                    buf.writeVarInt(payload.sectionKeys().size());
                    for (long key : payload.sectionKeys()) buf.writeLong(key);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
