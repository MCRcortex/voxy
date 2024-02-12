package me.cortex.voxy.client;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.VoxelCore;
import me.cortex.voxy.client.saver.WorldSelectionSystem;
import me.cortex.voxy.client.terrain.WorldImportCommand;
import me.cortex.voxy.common.storage.config.Serialization;
import me.cortex.voxy.common.storage.other.CompressionStorageAdaptor;
import me.cortex.voxy.common.storage.StorageBackend;
import me.cortex.voxy.common.storage.compressors.ZSTDCompressor;
import me.cortex.voxy.common.storage.other.FragmentedStorageBackendAdaptor;
import me.cortex.voxy.common.storage.rocksdb.RocksDBStorageBackend;
import me.cortex.voxy.common.world.WorldEngine;
import net.fabricmc.api.ClientModInitializer;
        import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.world.ClientWorld;

public class Voxy implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Serialization.init();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(WorldImportCommand.register());
        });
    }


    private static final WorldSelectionSystem selector = new WorldSelectionSystem();

    public static VoxelCore createVoxelCore(ClientWorld world) {
        var selection = selector.getBestSelectionOrCreate(world);
        return new VoxelCore(selection);
    }
}
