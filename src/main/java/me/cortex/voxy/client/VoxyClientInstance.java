package me.cortex.voxy.client;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.config.ConfigBuildCtx;
import me.cortex.voxy.common.config.Serialization;
import me.cortex.voxy.common.config.compressors.ZSTDCompressor;
import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.common.config.section.SectionStorage;
import me.cortex.voxy.common.config.section.SectionStorageConfig;
import me.cortex.voxy.common.config.storage.other.CompressionStorageAdaptor;
import me.cortex.voxy.common.config.storage.rocksdb.RocksDBStorageBackend;
import me.cortex.voxy.commonImpl.ImportManager;
import me.cortex.voxy.commonImpl.VoxyInstance;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class VoxyClientInstance extends VoxyInstance {
    public static boolean isInGame = false;

    private final SectionStorageConfig storageConfig;
    private final Path basePath = getBasePath();
    public VoxyClientInstance() {
        super(VoxyConfig.CONFIG.serviceThreads);
        try {
            Files.createDirectories(this.basePath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        this.storageConfig = getCreateStorageConfig(this.basePath);
    }

    @Override
    protected ImportManager createImportManager() {
        return new ClientImportManager();
    }

    @Override
    protected SectionStorage createStorage(WorldIdentifier identifier) {
        var ctx = new ConfigBuildCtx();
        ctx.setProperty(ConfigBuildCtx.BASE_SAVE_PATH, this.basePath.toString());
        ctx.setProperty(ConfigBuildCtx.WORLD_IDENTIFIER, getWorldId(identifier));
        ctx.pushPath(ConfigBuildCtx.DEFAULT_STORAGE_PATH);
        return this.storageConfig.build(ctx);
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

    private static String getWorldId(WorldIdentifier identifier) {
        String data = identifier.biomeSeed + identifier.key.toString();
        try {
            return bytesToHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes())).substring(0, 32);
        } catch (
                NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static SectionStorageConfig getCreateStorageConfig(Path path) {
        var json = path.resolve("config.json");
        Config config = null;
        if (Files.exists(json)) {
            try {
                config = Serialization.GSON.fromJson(Files.readString(json), Config.class);
                if (config == null) {
                    Logger.error("Config deserialization null, reverting to default");
                } else {
                    if (config.sectionStorageConfig == null) {
                        Logger.error("Config section storage null, reverting to default");
                        config = null;
                    }
                }
            } catch (Exception e) {
                Logger.error("Failed to load the storage configuration file, resetting it to default, this will probably break your save if you used a custom storage config", e);
            }
        }

        if (config == null) {
            config = DEFAULT_STORAGE_CONFIG;
        }
        try {
            Files.writeString(json, Serialization.GSON.toJson(config));
        } catch (Exception e) {
            throw new RuntimeException("Failed write the config, aborting!", e);
        }
        if (config == null) {
            throw new IllegalStateException("Config is still null\n");
        }
        return config.sectionStorageConfig;
    }

    private static class Config {
        public int version = 1;
        public SectionStorageConfig sectionStorageConfig;
    }
    private static final Config DEFAULT_STORAGE_CONFIG;
    static {
        var config = new Config();

        //Load the default config
        var baseDB = new RocksDBStorageBackend.Config();

        var compressor = new ZSTDCompressor.Config();
        compressor.compressionLevel = 1;

        var compression = new CompressionStorageAdaptor.Config();
        compression.delegate = baseDB;
        compression.compressor = compressor;

        var serializer = new SectionSerializationStorage.Config();
        serializer.storage = compression;
        config.sectionStorageConfig = serializer;

        DEFAULT_STORAGE_CONFIG = config;
    }

    private static Path getBasePath() {
        Path basePath = MinecraftClient.getInstance().runDirectory.toPath().resolve(".voxy").resolve("saves");
        var iserver = MinecraftClient.getInstance().getServer();
        if (iserver != null) {
            basePath = iserver.getSavePath(WorldSavePath.ROOT).resolve("voxy");
        } else {
            var netHandle = MinecraftClient.getInstance().interactionManager;
            if (netHandle == null) {
                Logger.error("Network handle null");
                basePath = basePath.resolve("UNKNOWN");
            } else {
                var info = netHandle.networkHandler.getServerInfo();
                if (info == null) {
                    Logger.error("Server info null");
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
        return basePath.toAbsolutePath();
    }
}
