package me.cortex.voxy.server;

import me.cortex.voxy.common.lod.WorldManifest;
import me.cortex.voxy.network.payload.RequestManifestPayload;
import me.cortex.voxy.network.payload.RequestSectionsPayload;
import me.cortex.voxy.network.payload.WorldManifestPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Handles inbound network packets sent by clients to the server.
 */
public final class ServerNetworkHandler {
    private ServerNetworkHandler() {}

    /**
     * Handles {@link RequestManifestPayload}: builds the section manifest for the
     * requested dimension and sends it back to the requesting client.
     */
    public static void handleRequestManifest(RequestManifestPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            ServerLodManager lodManager = VoxyServer.getLodManager();
            if (lodManager == null) return;

            WorldManifest manifest = lodManager.buildManifest(payload.dimension());
            WorldManifestPayload response = new WorldManifestPayload(payload.dimension(), manifest);
            ctx.reply(response);
        });
    }

    /**
     * Handles {@link RequestSectionsPayload}: streams the requested LOD sections back
     * to the client.
     */
    public static void handleRequestSections(RequestSectionsPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            ServerLodManager lodManager = VoxyServer.getLodManager();
            if (lodManager == null) return;

            lodManager.sendSectionsToPlayer(player, payload.dimension(), payload.sectionKeys());
        });
    }
}
