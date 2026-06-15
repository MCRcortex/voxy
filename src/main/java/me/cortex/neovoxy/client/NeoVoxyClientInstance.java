package me.cortex.neovoxy.client;

import me.cortex.neovoxy.client.compat.FlashbackCompat;
import me.cortex.neovoxy.client.config.NeoVoxyConfig;
import me.cortex.neovoxy.client.mixin.sodium.AccessorSodiumWorldRenderer;
import me.cortex.neovoxy.common.Logger;
import me.cortex.neovoxy.common.StorageConfigUtil;
import me.cortex.neovoxy.common.config.ConfigBuildCtx;
import me.cortex.neovoxy.common.config.Serialization;
import me.cortex.neovoxy.common.config.compressors.ZSTDCompressor;
import me.cortex.neovoxy.common.config.section.SectionSerializationStorage;
import me.cortex.neovoxy.common.config.section.SectionStorage;
import me.cortex.neovoxy.common.config.section.SectionStorageConfig;
import me.cortex.neovoxy.common.config.storage.other.CompressionStorageAdaptor;
import me.cortex.neovoxy.common.config.storage.rocksdb.RocksDBStorageBackend;
import me.cortex.neovoxy.commonImpl.ImportManager;
import me.cortex.neovoxy.commonImpl.NeoVoxyInstance;
import me.cortex.neovoxy.commonImpl.WorldIdentifier;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.Files;
import java.nio.file.Path;

public class NeoVoxyClientInstance extends NeoVoxyInstance {
    public static boolean isInGame = false;

    private final SectionStorageConfig storageConfig;
    private final Path basePath;
    private final boolean noIngestOverride;
    public NeoVoxyClientInstance() {
        super();
        var path = FlashbackCompat.getReplayStoragePath();
        this.noIngestOverride = path != null;
        if (path == null) {
            path = getBasePath();
        }
        this.basePath = path;
        this.storageConfig = StorageConfigUtil.getCreateStorageConfig(Config.class, c->c.version==1&&c.sectionStorageConfig!=null, ()->DEFAULT_STORAGE_CONFIG, path).sectionStorageConfig;
        this.updateDedicatedThreads();
    }

    @Override
    public void updateDedicatedThreads() {
        int target = NeoVoxyConfig.CONFIG.serviceThreads;
        if (!NeoVoxyConfig.CONFIG.dontUseSodiumBuilderThreads) {
            var swr = SodiumWorldRenderer.instanceNullable();
            if (swr != null) {
                var rsm = ((AccessorSodiumWorldRenderer) swr).getRenderSectionManager();
                if (rsm != null) {
                    this.setNumThreads(Math.max(1, target - rsm.getBuilder().getTotalThreadCount()));
                    return;
                }
            }
        }
        this.setNumThreads(target);
    }

    @Override
    protected ImportManager createImportManager() {
        return new ClientImportManager();
    }

    @Override
    protected SectionStorage createStorage(WorldIdentifier identifier) {
        var ctx = new ConfigBuildCtx();
        ctx.setProperty(ConfigBuildCtx.BASE_SAVE_PATH, this.basePath.toString());
        ctx.setProperty(ConfigBuildCtx.WORLD_IDENTIFIER, identifier.getWorldId());
        ctx.pushPath(ConfigBuildCtx.DEFAULT_STORAGE_PATH);
        return this.storageConfig.build(ctx);
    }

    public Path getStorageBasePath() {
        return this.basePath;
    }

    @Override
    public boolean isIngestEnabled(WorldIdentifier worldId) {
        return (!this.noIngestOverride) && NeoVoxyConfig.CONFIG.ingestEnabled;
    }

    private static class Config {
        public int version = 1;
        public SectionStorageConfig sectionStorageConfig;
    }

    private static final Config DEFAULT_STORAGE_CONFIG;
    static {
        var config = new Config();
        config.sectionStorageConfig = StorageConfigUtil.createDefaultSerializer();
        DEFAULT_STORAGE_CONFIG = config;
    }

    private static Path getBasePath() {
        Path basePath = Minecraft.getInstance().gameDirectory.toPath().resolve(".neovoxy").resolve("saves");
        var iserver = Minecraft.getInstance().getSingleplayerServer();
        if (iserver != null) {
            basePath = iserver.getWorldPath(LevelResource.ROOT).resolve("neovoxy");
        } else {
            var netHandle = Minecraft.getInstance().gameMode;
            if (netHandle == null) {
                Logger.error("Network handle null");
                basePath = basePath.resolve("UNKNOWN");
            } else {
                var info = netHandle.connection.getServerData();
                if (info == null) {
                    Logger.error("Server info null");
                    basePath = basePath.resolve("UNKNOWN");
                } else {
                    if (info.isRealm()) {
                        basePath = basePath.resolve("realms");
                    } else {
                        basePath = basePath.resolve(info.ip.replace(":", "_"));
                    }
                }
            }
        }
        return basePath.toAbsolutePath();
    }
}
