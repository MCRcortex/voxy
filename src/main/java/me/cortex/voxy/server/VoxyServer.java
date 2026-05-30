package me.cortex.voxy.server;

import me.cortex.voxy.common.storage.SectionStorageManager;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the server-side Voxy subsystem.
 *
 * <p>Registers server lifecycle events and owns the singleton {@link ServerLodManager}.
 * Register via {@link #register(IEventBus)} from the mod constructor.
 */
public final class VoxyServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(VoxyServer.class);

    private static volatile ServerLodManager lodManager;

    private VoxyServer() {}

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(VoxyServer::onServerStarting);
        NeoForge.EVENT_BUS.addListener(VoxyServer::onServerStopping);
    }

    private static void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        LOGGER.info("[Voxy] Server starting — initialising LOD manager");
        ServerLodManager mgr = new ServerLodManager(server);
        lodManager = mgr;
        // Register chunk/level event listeners on the NeoForge bus
        new ChunkEventHandler(mgr, NeoForge.EVENT_BUS);
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        ServerLodManager mgr = lodManager;
        if (mgr != null) {
            LOGGER.info("[Voxy] Server stopping — shutting down LOD manager");
            lodManager = null;
            mgr.close();
        }
    }

    /** Returns the active {@link ServerLodManager}, or {@code null} if the server is not running. */
    public static ServerLodManager getLodManager() {
        return lodManager;
    }
}
