package me.cortex.voxy.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C→S: client asks the server to send the {@link WorldManifestPayload} for a dimension.
 */
public record RequestManifestPayload(ResourceLocation dimension) implements CustomPacketPayload {

    public static final Type<RequestManifestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("voxy", "request_manifest"));

    public static final StreamCodec<FriendlyByteBuf, RequestManifestPayload> CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC,
                    RequestManifestPayload::dimension,
                    RequestManifestPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
