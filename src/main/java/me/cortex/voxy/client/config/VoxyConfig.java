package me.cortex.voxy.client.config;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

public class VoxyConfig {
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(Modifier.PRIVATE)
            .create();
    public static VoxyConfig CONFIG = loadOrCreate();

    public boolean enabled = true;
    public int qualityScale = 20;
    public int maxSections = 200_000;
    public int geometryBufferSize = (1<<30)/8;
    public int ingestThreads = 5;
    public int savingThreads = 10;
    public int renderThreads = 5;
    public int savingCompressionLevel = 7;
    public String storagePath = "voxy_db";

    transient StorageConfig storageConfig;
    public static abstract class StorageConfig { }
    public static class FragmentedStorageConfig extends StorageConfig { }
    public static class LmdbStorageConfig extends StorageConfig { }





    public static VoxyConfig loadOrCreate() {
        var path = getConfigPath();
        if (Files.exists(path)) {
            try (FileReader reader = new FileReader(path.toFile())) {
                return GSON.fromJson(reader, VoxyConfig.class);
            } catch (IOException e) {
                System.err.println("Could not parse config");
                e.printStackTrace();
            }
        }
        return new VoxyConfig();
    }
    public void save() {
        //Unsafe, todo: fixme! needs to be atomic!
        try {
            Files.writeString(getConfigPath(), GSON.toJson(this));
        } catch (IOException e) {
            System.err.println("Failed to write config file");
            e.printStackTrace();
        }
    }

    private static Path getConfigPath() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve("voxy-config.json");
    }

}
