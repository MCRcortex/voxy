package me.cortex.voxy.network.payload;

import me.cortex.voxy.common.lod.WorldManifest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * S→C: server sends the complete section key→hash manifest for a dimension.
 * The map is GZIP-compressed so large worlds transfer efficiently.
 */
public record WorldManifestPayload(
        ResourceLocation dimension,
        WorldManifest manifest
) implements CustomPacketPayload {

    public static final Type<WorldManifestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("voxy", "world_manifest"));

    public static final StreamCodec<FriendlyByteBuf, WorldManifestPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public WorldManifestPayload decode(FriendlyByteBuf buf) {
                    ResourceLocation dim = ResourceLocation.STREAM_CODEC.decode(buf);
                    byte[] compressed = buf.readByteArray();
                    Map<Long, Long> map = decompress(compressed);
                    return new WorldManifestPayload(dim, new WorldManifest(map));
                }

                @Override
                public void encode(FriendlyByteBuf buf, WorldManifestPayload payload) {
                    ResourceLocation.STREAM_CODEC.encode(buf, payload.dimension());
                    buf.writeByteArray(compress(payload.manifest().asMap()));
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // -------------------------------------------------------------------------

    private static byte[] compress(Map<Long, Long> map) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(baos);
             DataOutputStream dos = new DataOutputStream(gzip)) {
            dos.writeInt(map.size());
            for (Map.Entry<Long, Long> e : map.entrySet()) {
                dos.writeLong(e.getKey());
                dos.writeLong(e.getValue());
            }
            dos.flush();
            gzip.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<Long, Long> decompress(byte[] data) {
        try (DataInputStream dis = new DataInputStream(
                new GZIPInputStream(new ByteArrayInputStream(data)))) {
            int count = dis.readInt();
            Map<Long, Long> map = new HashMap<>(count);
            for (int i = 0; i < count; i++) {
                long key  = dis.readLong();
                long hash = dis.readLong();
                map.put(key, hash);
            }
            return map;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
