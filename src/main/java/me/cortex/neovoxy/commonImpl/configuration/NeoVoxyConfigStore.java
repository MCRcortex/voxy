package me.cortex.neovoxy.commonImpl.configuration;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import me.cortex.neovoxy.common.Logger;
import me.cortex.neovoxy.common.util.MultiGson;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class NeoVoxyConfigStore {
    private final MultiGson gson;

    private NeoVoxyConfigStore(Object... defaultValues) {
        var gb = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .setPrettyPrinting()
                .excludeFieldsWithModifiers(Modifier.PRIVATE);
        Map<Class<?>, Object> defaultValueMap = new HashMap<>();
        var mgb = new MultiGson.Builder(gb);
        for (var i : defaultValues) {
            mgb.add(i.getClass());
            defaultValueMap.put(i.getClass(), i);
        }
        gb.registerTypeAdapterFactory(new TypeAdapterFactory() {
            @Override
            public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
                if (defaultValueMap.containsKey(typeToken.getRawType())) {
                    var defVal = (T)defaultValueMap.get(typeToken.getRawType());
                    var adapter = gson.getDelegateAdapter(this, typeToken);
                    return new TypeAdapter<T>() {
                        @Override
                        public void write(JsonWriter writer, T obj) throws IOException {
                            var defJson = adapter.toJsonTree(defVal).getAsJsonObject();
                            var val = adapter.toJsonTree(obj).getAsJsonObject();
                            for (var key : defJson.keySet()) {
                                if (val.has(key)) {
                                    if (defJson.get(key).equals(val.get(key))) {
                                        val.addProperty(key, "DEFAULT_VALUE");
                                    }
                                }
                            }
                            gson.toJson(val, writer);
                        }

                        @Override
                        public T read(JsonReader reader) throws IOException {
                            var defJson = adapter.toJsonTree(defVal).getAsJsonObject();
                            var val = ((JsonElement)gson.fromJson(reader, JsonElement.class)).getAsJsonObject();
                            for (var key : defJson.keySet()) {
                                if (val.has(key)) {
                                    if (val.get(key).equals(new JsonPrimitive("DEFAULT_VALUE"))) {
                                        val.add(key, defJson.get(key));
                                    }
                                }
                            }
                            return adapter.fromJsonTree(val);
                        }
                    };
                }
                return null;
            }
        });
        this.gson = mgb.build();
    }

    /*
    private static void loadOrCreate() {
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

     */

    public void save() {
        try {
            Files.writeString(getConfigPath(), this.gson.toJson(this));
        } catch (IOException e) {
            Logger.error("Failed to write config file", e);
        }
    }

    private static Path getConfigPath() {
        return FMLPaths.CONFIGDIR.get()
                .resolve("neovoxy-config.json");
    }
}
