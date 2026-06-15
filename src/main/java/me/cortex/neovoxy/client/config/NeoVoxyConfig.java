package me.cortex.neovoxy.client.config;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.cortex.neovoxy.common.Logger;
import me.cortex.neovoxy.common.util.cpu.CpuLayout;
import me.cortex.neovoxy.commonImpl.NeoVoxyCommon;
import net.neoforged.fml.loading.FMLPaths;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

public class NeoVoxyConfig {
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(Modifier.PRIVATE)
            .create();

    public static NeoVoxyConfig CONFIG = loadOrCreate();

    public boolean enabled = true;
    public boolean enableRendering = true;
    public boolean ingestEnabled = true;
    public int sectionRenderDistance = 16;
    public int serviceThreads = (int) Math.max(CpuLayout.getCoreCount()/1.5, 1);
    public float subDivisionSize = 64;
    public boolean useEnvironmentalFog = true;
    public boolean dontUseSodiumBuilderThreads = false;

    // LOD boundary buffer: controls the safety margin between vanilla chunks and LOD rendering
    // Higher values = more overlap, prevents pop-in at chunk boundaries when flying
    // Range: 0-4 blocks, default 1 (original NeoVoxy behavior)
    public int lodBoundaryBuffer = 1;

    // World curvature: simulates standing on a spherical planet
    // 0 = disabled (flat world)
    // 1 = real Earth curvature (6371km radius)
    // Higher values = more extreme curvature (smaller planet effect)
    // Range: 0, or 50-5000 (values 1-49 are invalid and auto-corrected to 50)
    // Inspired by Distant Horizons' earth curvature feature
    public int earthCurveRatio = 0;

    private static NeoVoxyConfig loadOrCreate() {
        if (NeoVoxyCommon.isAvailable()) {
            var path = getConfigPath();
            if (Files.exists(path)) {
                try (FileReader reader = new FileReader(path.toFile())) {
                    var conf = GSON.fromJson(reader, NeoVoxyConfig.class);
                    if (conf != null) {
                        conf.save();
                        return conf;
                    } else {
                        Logger.error("Failed to load neovoxy config, resetting");
                    }
                } catch (IOException e) {
                    Logger.error("Could not parse config", e);
                }
            }
            var config = new NeoVoxyConfig();
            config.save();
            return config;
        } else {
            var config = new NeoVoxyConfig();
            config.enabled = false;
            config.enableRendering = false;
            return config;
        }
    }

    public void save() {
        try {
            Files.writeString(getConfigPath(), GSON.toJson(this));
        } catch (IOException e) {
            Logger.error("Failed to write config file", e);
        }
    }

    private static Path getConfigPath() {
        return FMLPaths.CONFIGDIR.get()
                .resolve("neovoxy-config.json");
    }

    public boolean isRenderingEnabled() {
        return NeoVoxyCommon.isAvailable() && this.enabled && this.enableRendering;
    }
}
