package me.cortex.voxy.client;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.VoxelCore;
import me.cortex.voxy.client.mixin.minecraft.MixinWorldRenderer;
import me.cortex.voxy.client.terrain.WorldImportCommand;
import me.cortex.voxy.common.storage.CompressionStorageAdaptor;
import me.cortex.voxy.common.storage.FragmentedStorageBackendAdaptor;
import me.cortex.voxy.common.storage.StorageBackend;
import me.cortex.voxy.common.storage.ZSTDCompressor;
import me.cortex.voxy.common.storage.rocksdb.RocksDBStorageBackend;
import me.cortex.voxy.common.world.WorldEngine;
import net.fabricmc.api.ClientModInitializer;
        import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;

import java.io.File;

public class Voxy implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(WorldImportCommand.register());
        });
    }

    public static VoxelCore createVoxelCore(ClientWorld world) {
        StorageBackend storage = new RocksDBStorageBackend(new File(VoxyConfig.CONFIG.storagePath));
        //StorageBackend storage = new FragmentedStorageBackendAdaptor(new File(VoxyConfig.CONFIG.storagePath));
        storage = new CompressionStorageAdaptor(new ZSTDCompressor(VoxyConfig.CONFIG.savingCompressionLevel), storage);
        var engine = new WorldEngine(storage, VoxyConfig.CONFIG.ingestThreads, VoxyConfig.CONFIG.savingThreads, 5);
        return new VoxelCore(engine);
    }
}
