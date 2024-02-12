package me.cortex.voxy.client.saver;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.common.storage.StorageBackend;
import me.cortex.voxy.common.storage.compressors.ZSTDCompressor;
import me.cortex.voxy.common.storage.config.ConfigBuildCtx;
import me.cortex.voxy.common.storage.other.CompressionStorageAdaptor;
import me.cortex.voxy.common.storage.other.TranslocatingStorageAdaptor;
import me.cortex.voxy.common.storage.rocksdb.RocksDBStorageBackend;
import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.client.world.ClientWorld;

import java.nio.file.Path;
import java.util.List;

//Sets up a world engine with respect to the world the client is currently loaded into
// this is a bit tricky as each world has its own config, e.g. storage configuration
public class WorldSelectionSystem {
    public static class Selection {
        public WorldEngine createEngine() {
            var baseDB = new RocksDBStorageBackend.Config();
            baseDB.path = VoxyConfig.CONFIG.storagePath;

            var compressor = new ZSTDCompressor.Config();
            compressor.compressionLevel = VoxyConfig.CONFIG.savingCompressionLevel;

            var compression = new CompressionStorageAdaptor.Config();
            compression.delegate = baseDB;
            compression.compressor = compressor;

            var translocator = new TranslocatingStorageAdaptor.Config();
            translocator.delegate = compression;
            translocator.transforms.add(new TranslocatingStorageAdaptor.BoxTransform(0,5,0, 200, 64, 200, 0, -5, 0));

            var ctx = new ConfigBuildCtx();
            var storage = translocator.build(ctx);
            return new WorldEngine(storage, VoxyConfig.CONFIG.ingestThreads, VoxyConfig.CONFIG.savingThreads, 5);

            //StorageBackend storage = new RocksDBStorageBackend(VoxyConfig.CONFIG.storagePath);
            ////StorageBackend storage = new FragmentedStorageBackendAdaptor(new File(VoxyConfig.CONFIG.storagePath));
            //storage = new CompressionStorageAdaptor(new ZSTDCompressor(VoxyConfig.CONFIG.savingCompressionLevel), storage);
            //return new WorldEngine(storage, VoxyConfig.CONFIG.ingestThreads, VoxyConfig.CONFIG.savingThreads, 5);
        }

        //Saves the config for the world selection or something, need to figure out how to make it work with dimensional configs maybe?
        // or just have per world config, cause when creating the world engine doing the string substitution would
        // make it automatically select the right id
        public void save() {

        }
    }


    //The way this works is saves are segmented into base worlds, e.g. server ip, local save etc
    // these are then segmented into subsaves for different worlds within the parent
    public WorldSelectionSystem() {

    }


    public Selection getBestSelectionOrCreate(ClientWorld world) {
        return new Selection();
    }
}
