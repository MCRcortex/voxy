package me.cortex.voxy.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the client-side Voxy subsystem.
 *
 * <p>Register via {@link #register(IEventBus)} from the mod constructor (client dist only).
 */
public final class VoxyClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(VoxyClient.class);

    private static volatile ClientLodManager lodManager;

    private VoxyClient() {}

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(VoxyClient::onPlayerJoinServer);
        NeoForge.EVENT_BUS.addListener(VoxyClient::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(VoxyClient::onRespawn);
    }

    private static void onPlayerJoinServer(ClientPlayerNetworkEvent.LoggingIn event) {
        String address = ClientLodManager.resolveServerAddress();
        LOGGER.info("[Voxy] Client connected to '{}' — creating LOD manager", address);
        ClientLodManager mgr = new ClientLodManager(address);
        lodManager = mgr;

        // Request manifest for the initial dimension
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level != null) {
            net.minecraft.resources.ResourceLocation dim = mc.level.dimension().location();
            mgr.onDimensionJoin(dim);
        }
    }

    private static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientLodManager mgr = lodManager;
        if (mgr != null) {
            LOGGER.info("[Voxy] Client disconnected — shutting down LOD manager");
            lodManager = null;
            mgr.close();
        }
    }

    private static void onRespawn(ClientPlayerNetworkEvent.Clone event) {
        // Respawn in a new dimension: request manifest for the new level
        ClientLodManager mgr = lodManager;
        if (mgr == null) return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level != null) {
            net.minecraft.resources.ResourceLocation dim = mc.level.dimension().location();
            mgr.onDimensionJoin(dim);
        }
    }

    /** Returns the active {@link ClientLodManager}, or {@code null} when not connected. */
    public static ClientLodManager getLodManager() {
        return lodManager;
    }
}
