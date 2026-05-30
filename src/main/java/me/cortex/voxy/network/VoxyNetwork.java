package me.cortex.voxy.network;

import me.cortex.voxy.network.payload.*;
import me.cortex.voxy.server.ServerNetworkHandler;
import me.cortex.voxy.client.ClientNetworkHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers all Voxy network payloads and their handlers.
 *
 * <p>All payloads use protocol version "1".  When the packet format changes,
 * bump {@link me.cortex.voxy.VoxyConstants#PROTOCOL_VERSION} and update here.
 */
public final class VoxyNetwork {
    private VoxyNetwork() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(VoxyNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // C→S: client requests the manifest for a dimension
        registrar.playToServer(
                RequestManifestPayload.TYPE,
                RequestManifestPayload.CODEC,
                ServerNetworkHandler::handleRequestManifest
        );

        // C→S: client requests specific missing/stale sections
        registrar.playToServer(
                RequestSectionsPayload.TYPE,
                RequestSectionsPayload.CODEC,
                ServerNetworkHandler::handleRequestSections
        );

        // S→C: server sends the full manifest for a dimension
        registrar.playToClient(
                WorldManifestPayload.TYPE,
                WorldManifestPayload.CODEC,
                ClientNetworkHandler::handleWorldManifest
        );

        // S→C: server delivers section data (bulk transfer or live update)
        registrar.playToClient(
                LodSectionDataPayload.TYPE,
                LodSectionDataPayload.CODEC,
                ClientNetworkHandler::handleLodSectionData
        );

        // S→C: server removes a section from the client cache
        registrar.playToClient(
                SectionRemovePayload.TYPE,
                SectionRemovePayload.CODEC,
                ClientNetworkHandler::handleSectionRemove
        );
    }
}
