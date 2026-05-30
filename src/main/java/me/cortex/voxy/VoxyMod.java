package me.cortex.voxy;

import me.cortex.voxy.client.VoxyClient;
import me.cortex.voxy.common.config.VoxyConfig;
import me.cortex.voxy.network.VoxyNetwork;
import me.cortex.voxy.server.VoxyServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge mod entry point for Voxy LOD.
 *
 * <p>Architecture overview:
 * <ul>
 *   <li><b>Server side</b>: {@link me.cortex.voxy.server.VoxyServer} listens for chunk load events,
 *       voxelizes chunks, stores LOD data in a per-world SQLite database, and streams
 *       changed sections to all connected clients.
 *   <li><b>Client side</b>: {@link VoxyClient} maintains a disk cache of received LOD
 *       sections (keyed by server address + dimension) and only requests sections that are
 *       missing or hash-mismatched, minimising redundant transfers.
 *   <li><b>Network</b>: five custom {@link net.minecraft.network.protocol.common.custom.CustomPacketPayload}
 *       types handle the manifest/diff/transfer protocol.
 * </ul>
 */
@Mod(VoxyConstants.MOD_ID)
public final class VoxyMod {

    public static final Logger LOGGER = LoggerFactory.getLogger(VoxyConstants.MOD_ID);

    public VoxyMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register common config (loaded on both sides)
        modContainer.registerConfig(ModConfig.Type.COMMON, VoxyConfig.SPEC);

        // Register network payloads
        VoxyNetwork.register(modEventBus);

        // Register server-side subsystem (runs on both Dist — server code is guarded by
        // server lifecycle events which only fire on a logical server)
        VoxyServer.register(modEventBus);

        // Register client-side subsystem only on the client dist
        if (FMLEnvironment.dist == Dist.CLIENT) {
            VoxyClient.register(modEventBus);
        }

        LOGGER.info("[Voxy] NeoForge mod initialised (protocol v{})", VoxyConstants.PROTOCOL_VERSION);
    }
}
