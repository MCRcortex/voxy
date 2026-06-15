package me.cortex.neovoxy.common.config;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import me.cortex.neovoxy.common.Logger;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public class Serialization {
    public static final Set<Class<?>> CONFIG_TYPES = new HashSet<>();
    public static Gson GSON;

    private static final class GsonConfigSerialization <T> implements TypeAdapterFactory {
        private final String typeField = "TYPE";
        private final Class<T> clz;

        private final Map<String, Class<? extends T>> name2type = new HashMap<>();
        private final Map<Class<? extends T>, String> type2name = new HashMap<>();

        private GsonConfigSerialization(Class<T> clz) {
            this.clz = clz;
        }

        public GsonConfigSerialization<T> register(String typeName, Class<? extends T> cls) {
            if (this.name2type.put(typeName, cls) != null) {
                throw new IllegalStateException("Type name already registered: " + typeName);
            }
            if (this.type2name.put(cls, typeName) != null) {
                throw new IllegalStateException("Class already registered with type name: " + typeName + ", " + cls);
            }
            return this;
        }


        private T deserialize(Gson gson, JsonElement json) {
            var retype = this.name2type.get(json.getAsJsonObject().remove(this.typeField).getAsString());
            return gson.getDelegateAdapter(this, TypeToken.get(retype)).fromJsonTree(json);
        }

        private JsonElement serialize(Gson gson, T value) {
            String name = this.type2name.get(value.getClass());
            if (name == null) {
                name = "UNKNOWN_TYPE_{" + value.getClass().getName() + "}";
            }

            var vjson = gson
                    .getDelegateAdapter(this, TypeToken.get((Class<T>) value.getClass()))
                    .toJsonTree(value);
            //All of this is so that the config_type is at the top :blob_face:
            var json = new JsonObject();
            json.addProperty(this.typeField, name);
            vjson.getAsJsonObject().asMap().forEach(json::add);
            return json;
        }


        @Override
        public <X> TypeAdapter<X> create(Gson gson, TypeToken<X> type) {
            if (this.clz.isAssignableFrom(type.getRawType())) {
                var jsonObjectAdapter = gson.getAdapter(JsonElement.class);

                return (TypeAdapter<X>) new TypeAdapter<T>() {
                    @Override
                    public void write(JsonWriter out, T value) throws IOException {
                        jsonObjectAdapter.write(out, GsonConfigSerialization.this.serialize(gson, value));
                    }

                    @Override
                    public T read(JsonReader in) throws IOException {
                        var obj = jsonObjectAdapter.read(in);
                        return GsonConfigSerialization.this.deserialize(gson, obj);
                    }
                };
            }
            return null;
        }
    }

    public static void init() {
        Map<Class<?>, GsonConfigSerialization<?>> serializers = new HashMap<>();

        // NeoForge: Use Class.forName() with initialize=true to trigger static initializers
        // This matches NeoForge's pattern in GameTestHooks.java:76 and upstream Fabric behavior
        // Class literals (.class) only LOAD classes, they don't INITIALIZE them
        // Static initializers in base config classes (CompressorConfig, StorageConfig, etc.)
        // add themselves to CONFIG_TYPES, so we must initialize to populate CONFIG_TYPES
        String[] configClassNames = {
            // Compressor configs (extend CompressorConfig)
            "me.cortex.neovoxy.common.config.compressors.LZ4Compressor$Config",
            // LZMACompressor is commented out in source
            "me.cortex.neovoxy.common.config.compressors.ZSTDCompressor$Config",

            // Storage configs (extend StorageConfig)
            "me.cortex.neovoxy.common.config.storage.lmdb.LMDBStorageBackend$Config",
            "me.cortex.neovoxy.common.config.storage.inmemory.MemoryStorageBackend$Config",
            "me.cortex.neovoxy.common.config.storage.redis.RedisStorageBackend$Config",
            "me.cortex.neovoxy.common.config.storage.rocksdb.RocksDBStorageBackend$Config",
            "me.cortex.neovoxy.common.config.storage.other.ReadonlyCachingLayer$Config",
            "me.cortex.neovoxy.common.config.storage.other.CompressionStorageAdaptor$Config",
            "me.cortex.neovoxy.common.config.storage.other.ConditionalStorageBackendConfig",
            "me.cortex.neovoxy.common.config.storage.other.FragmentedStorageBackendAdaptor$Config",
            "me.cortex.neovoxy.common.config.storage.other.FragmentedStorageBackendAdaptor$Config2",
            "me.cortex.neovoxy.common.config.storage.other.BasicPathInsertionConfig",

            // Section storage configs (extend SectionStorageConfig)
            "me.cortex.neovoxy.common.config.section.SectionSerializationStorage$Config"
        };

        int count = 0;
        outer:
        for (String className : configClassNames) {
            try {
                // Use 3-arg Class.forName with initialize=true to run static initializers
                // This populates CONFIG_TYPES via parent class static blocks
                Class<?> original = Class.forName(className, true, Serialization.class.getClassLoader());

                if (Modifier.isAbstract(original.getModifiers())) {
                    continue; // Don't register abstract classes
                }

                Class<?> clz = original;
                while ((clz = clz.getSuperclass()) != null) {
                    if (CONFIG_TYPES.contains(clz)) {
                        Method nameMethod = null;
                        try {
                            nameMethod = original.getMethod("getConfigTypeName");
                            nameMethod.setAccessible(true);
                        } catch (NoSuchMethodException e) {}
                        if (nameMethod == null) {
                            Logger.error("WARNING: Config class " + className + " doesnt contain a getConfigTypeName and thus wont be serializable");
                            continue outer;
                        }
                        count++;
                        String name = (String) nameMethod.invoke(null);
                        serializers.computeIfAbsent(clz, GsonConfigSerialization::new)
                                .register(name, (Class) original);
                        Logger.info("Registered " + original.getSimpleName() + " as " + name + " for config type " + clz.getSimpleName());
                        break;
                    }
                }
            } catch (ClassNotFoundException e) {
                Logger.error("Config class not found: " + className, e);
            } catch (Exception e) {
                Logger.error("Error registering config class: " + className, e);
            }
        }

        var builder = new GsonBuilder()
                .setPrettyPrinting();
        for (var entry : serializers.entrySet()) {
            builder.registerTypeAdapterFactory(entry.getValue());
        }

        GSON = builder.create();
        Logger.info("Registered " + count + " config types");
    }

    // NOTE: collectAllClasses methods removed for NeoForge port
    // Dynamic discovery via ClassLoader.getResourceAsStream() doesn't work reliably
    // with NeoForge's module system and JarJar packaging. Using explicit registration instead.
}
