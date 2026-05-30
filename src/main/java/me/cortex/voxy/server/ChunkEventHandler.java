package me.cortex.voxy.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Listens for NeoForge server-side chunk and level events and routes them to
 * {@link ServerLodManager}.
 */
public final class ChunkEventHandler {

    private final ServerLodManager lodManager;

    public ChunkEventHandler(ServerLodManager lodManager, IEventBus neoForgeBus) {
        this.lodManager = lodManager;
        neoForgeBus.addListener(this::onChunkLoad);
        neoForgeBus.addListener(this::onLevelUnload);
    }

    private void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        lodManager.onChunkLoaded(serverLevel, chunk);
    }

    private void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        // Flush storage for the unloading dimension
        String dimKey = serverLevel.dimension().location().toString();
        // The storage manager handles cleanup when close() is called on server stop;
        // for hot-unloads (e.g. dynamic dimension removal) we explicitly unload.
        lodManager.getStorageManager().unload(dimKey);
    }
}
