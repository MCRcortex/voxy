package me.cortex.voxy.client.saver;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.common.storage.other.CompressionStorageAdaptor;
import me.cortex.voxy.common.storage.other.FragmentedStorageBackendAdaptor;
import me.cortex.voxy.common.storage.compressors.ZSTDCompressor;
import me.cortex.voxy.common.world.WorldEngine;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

//Sets up a world engine with respect to the world the client is currently loaded into
// this is a bit tricky as each world has its own config, e.g. storage configuration
public class SaveSelectionSystem {

    //The way this works is saves are segmented into base worlds, e.g. server ip, local save etc
    // these are then segmented into subsaves for different worlds within the parent
    public SaveSelectionSystem(List<Path> storagePaths) {

    }

    public WorldEngine createWorldEngine() {
        //TODO: have basicly a recursive config tree for StorageBackend
        // with a .build() method
        // also have a way for the config to specify and create a config "screen"

        // e.g. CompressionStorageAdaptorConfig(StorageCompressorConfig, StorageBackendConfig)
        // FragmentedStorageBackendAdaptorConfig(File)
        // RocksDBStorageBackendConfig(File)
        // RedisStorageBackendConfig(String, int, String)
        return null;
    }
}
