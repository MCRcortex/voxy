package me.cortex.voxy.client.config;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.cpu.CpuLayout;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.caffeinemc.mods.sodium.client.gui.options.storage.OptionStorage;
import net.fabricmc.loader.api.FabricLoader;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

public class VoxyConfig implements OptionStorage<VoxyConfig> {
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(Modifier.PRIVATE)
            .create();

    public static VoxyConfig CONFIG = loadOrCreate();

    public boolean enabled = true;
    public boolean enableRendering = true;
    public boolean ingestEnabled = true;
    public int sectionRenderDistance = 16;
    public int serviceThreads = (int) Math.max(CpuLayout.getCoreCount()/1.5, 1);
    public int lodLoadThreads = Math.max(1, CpuLayout.getCoreCount() / 4);
    public int lodIngestThreads = Math.max(1, CpuLayout.getCoreCount() / 4);
    public int lodMeshThreads = Math.max(1, CpuLayout.getCoreCount() / 4);
    public int lodSaveThreads = 1;
    public float subDivisionSize = 64;
    public boolean useEnvironmentalFog = true;
    public boolean dontUseSodiumBuilderThreads = false;

    private static VoxyConfig loadOrCreate() {
        if (VoxyCommon.isAvailable()) {
            var path = getConfigPath();
            if (Files.exists(path)) {
                try (FileReader reader = new FileReader(path.toFile())) {
                    var conf = GSON.fromJson(reader, VoxyConfig.class);
                    if (conf != null) {
                        conf.applyDefaults();
                        conf.save();
                        return conf;
                    } else {
                        Logger.error("Failed to load voxy config, resetting");
                    }
                } catch (IOException e) {
                    Logger.error("Could not parse config", e);
                }
            }
            Logger.info("Config doesnt exist, creating new");
            var config = new VoxyConfig();
            config.save();
            return config;
        } else {
            var config = new VoxyConfig();
            config.enabled = false;
            config.enableRendering = false;
            return config;
        }
    }

    @Override
    public VoxyConfig getData() {
        return this;
    }

    public void save() {
        if (!VoxyCommon.isAvailable()) {
            Logger.info("Not saving config since voxy is unavalible");
            return;
        }

        try {
            Files.writeString(getConfigPath(), GSON.toJson(this));
        } catch (IOException e) {
            Logger.error("Failed to write config file", e);
        }
    }

    private static Path getConfigPath() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve("voxy-config.json");
    }

    public boolean isRenderingEnabled() {
        return VoxyCommon.isAvailable() && this.enabled && this.enableRendering;
    }

    private void applyDefaults() {
        int cores = CpuLayout.getCoreCount();
        if (this.serviceThreads <= 0) {
            this.serviceThreads = (int) Math.max(cores / 1.5, 1);
        }
        if (this.lodLoadThreads <= 0) {
            this.lodLoadThreads = Math.max(1, cores / 4);
        }
        if (this.lodIngestThreads <= 0) {
            this.lodIngestThreads = Math.max(1, cores / 4);
        }
        if (this.lodMeshThreads <= 0) {
            this.lodMeshThreads = Math.max(1, cores / 4);
        }
        if (this.lodSaveThreads <= 0) {
            this.lodSaveThreads = 1;
        }
    }
}
