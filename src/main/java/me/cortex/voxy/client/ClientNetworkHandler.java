package me.cortex.voxy.client;

import me.cortex.voxy.common.lod.LodSection;
import me.cortex.voxy.common.lod.WorldManifest;
import me.cortex.voxy.network.payload.LodSectionDataPayload;
import me.cortex.voxy.network.payload.SectionRemovePayload;
import me.cortex.voxy.network.payload.WorldManifestPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Handles inbound network packets received by the client.
 * All handlers delegate to the active {@link ClientLodManager}.
 */
public final class ClientNetworkHandler {
    private ClientNetworkHandler() {}

    public static void handleWorldManifest(WorldManifestPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientLodManager mgr = VoxyClient.getLodManager();
            if (mgr == null) return;
            mgr.onManifestReceived(payload.dimension(), payload.manifest());
        });
    }

    public static void handleLodSectionData(LodSectionDataPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientLodManager mgr = VoxyClient.getLodManager();
            if (mgr == null) return;
            mgr.onSectionDataReceived(payload.dimension(), payload.sectionKey(),
                    payload.hash(), payload.data());
        });
    }

    public static void handleSectionRemove(SectionRemovePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientLodManager mgr = VoxyClient.getLodManager();
            if (mgr == null) return;
            mgr.onSectionRemoved(payload.dimension(), payload.sectionKey());
        });
    }
}
