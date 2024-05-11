package me.cortex.voxy.client.saver;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.common.storage.StorageBackend;
import me.cortex.voxy.common.storage.compressors.ZSTDCompressor;
import me.cortex.voxy.common.storage.config.ConfigBuildCtx;
import me.cortex.voxy.common.config.Serialization;
import me.cortex.voxy.common.storage.config.StorageConfig;
import me.cortex.voxy.common.storage.other.CompressionStorageAdaptor;
import me.cortex.voxy.common.storage.rocksdb.RocksDBStorageBackend;
import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

//Sets up a world engine with respect to the world the client is currently loaded into
// this is a bit tricky as each world has its own config, e.g. storage configuration
public class ContextSelectionSystem {
    public static class WorldConfig {
        public int minYOverride = Integer.MAX_VALUE;
        public int maxYOverride = Integer.MIN_VALUE;
        public StorageConfig storageConfig;
    }
    public static final String DEFAULT_STORAGE_CONFIG;
    static {
        var config = new WorldConfig();

        //Load the default config
        var baseDB = new RocksDBStorageBackend.Config();

        var compressor = new ZSTDCompressor.Config();
        compressor.compressionLevel = 7;

        var compression = new CompressionStorageAdaptor.Config();
        compression.delegate = baseDB;
        compression.compressor = compressor;

        config.storageConfig = compression;
        DEFAULT_STORAGE_CONFIG = Serialization.GSON.toJson(config);

        if (Serialization.GSON.fromJson(DEFAULT_STORAGE_CONFIG, WorldConfig.class) == null) {
            throw new IllegalStateException();
        }
    }

    public static class Selection {
        private final Path selectionFolder;
        private final String worldId;

        private WorldConfig config;

        public Selection(Path selectionFolder, String worldId) {
            this.selectionFolder = selectionFolder;
            this.worldId = worldId;
            loadStorageConfigOrDefault();
        }

        private void loadStorageConfigOrDefault() {
            var json = this.selectionFolder.resolve("config.json");

            if (Files.exists(json)) {
                try {
                    this.config = Serialization.GSON.fromJson(Files.readString(json), WorldConfig.class);
                    if (this.config == null) {
                        throw new IllegalStateException("Config deserialization null, reverting to default");
                    }
                    return;
                } catch (Exception e) {
                    System.err.println("Failed to load the storage configuration file, resetting it to default");
                    e.printStackTrace();
                }
            }

            try {
                this.config = Serialization.GSON.fromJson(VoxyConfig.CONFIG.defaultSaveConfig, WorldConfig.class);
                this.save();
            } catch (Exception e) {
                throw new RuntimeException("Failed to deserialize the default config, aborting!", e);
            }
            if (this.config == null) {
                throw new IllegalStateException("Config is still null: \n"+VoxyConfig.CONFIG.defaultSaveConfig);
            }
        }

        public StorageBackend createStorageBackend() {
            var ctx = new ConfigBuildCtx();
            ctx.setProperty(ConfigBuildCtx.BASE_SAVE_PATH, this.selectionFolder.toString());
            ctx.setProperty(ConfigBuildCtx.WORLD_IDENTIFIER, this.worldId);
            ctx.pushPath(ConfigBuildCtx.DEFAULT_STORAGE_PATH);
            return this.config.storageConfig.build(ctx);
        }

        public WorldEngine createEngine() {
            return new WorldEngine(this.createStorageBackend(), VoxyConfig.CONFIG.ingestThreads, VoxyConfig.CONFIG.savingThreads, 5);
        }

        //Saves the config for the world selection or something, need to figure out how to make it work with dimensional configs maybe?
        // or just have per world config, cause when creating the world engine doing the string substitution would
        // make it automatically select the right id
        public void save() {
            var file = this.selectionFolder.resolve("config.json");
            var json = Serialization.GSON.toJson(this.config);
            try {
                Files.writeString(file, json);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public WorldConfig getConfig() {
            return this.config;
        }
    }

    //Gets dimension independent base world, if singleplayer, its the world name, if multiplayer, its the server ip
    private static Path getBasePath(ClientWorld world) {
        //TODO: improve this
        Path basePath = MinecraftClient.getInstance().runDirectory.toPath().resolve(".voxy").resolve("saves");
        var iserver = MinecraftClient.getInstance().getServer();
        if (iserver != null) {
            basePath = iserver.getSavePath(WorldSavePath.ROOT).resolve("voxy");
        } else {
            var netHandle = MinecraftClient.getInstance().interactionManager;
            if (netHandle == null) {
                System.err.println("Network handle null");
                basePath = basePath.resolve("UNKNOWN");
            } else {
                var info = netHandle.networkHandler.getServerInfo();
                if (info == null) {
                    System.err.println("Server info null");
                    basePath = basePath.resolve("UNKNOWN");
                } else {
                    if (info.isRealm()) {
                        basePath = basePath.resolve("realms");
                    } else {
                        basePath = basePath.resolve(info.address.replace(":", "_"));
                    }
                }
            }
        }
        return basePath;
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private static String getWorldId(ClientWorld world) {
        String data = world.getBiomeAccess().seed + world.getRegistryKey().toString();
        try {
            return bytesToHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes())).substring(0, 32);
        } catch (
                NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    //The way this works is saves are segmented into base worlds, e.g. server ip, local save etc
    // these are then segmented into subsaves for different worlds within the parent
    public ContextSelectionSystem() {
    }


    public Selection getBestSelectionOrCreate(ClientWorld world) {
        var path = getBasePath(world);
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new Selection(path, getWorldId(world));
    }
}
