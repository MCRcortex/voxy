package me.cortex.voxy.common.storage.config;

import com.google.gson.*;
import com.google.gson.internal.bind.JsonTreeWriter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

public class Serialization {
    public static final Set<Class<?>> CONFIG_TYPES = new HashSet<>();
    public static final Gson GSON;

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

    static {
        Map<Class<?>, GsonConfigSerialization<?>> serializers = new HashMap<>();

        Set<String> clazzs = new LinkedHashSet<>();
        var path = FabricLoader.getInstance().getModContainer("voxy").get().getRootPaths().get(0);
        clazzs.addAll(collectAllClasses(path, "me.cortex.voxy.common.storage"));
        clazzs.addAll(collectAllClasses("me.cortex.voxy.common.storage"));

        outer:
        for (var clzName : clazzs) {
            if (clzName.equals(Serialization.class.getName())) {
                continue;//Dont want to load ourselves
            }

            try {
                var clz = Class.forName(clzName);
                var original = clz;
                while ((clz = clz.getSuperclass()) != null) {
                    if (CONFIG_TYPES.contains(clz)) {
                        Method nameMethod = null;
                        try {
                            nameMethod = original.getMethod("getConfigTypeName");
                            nameMethod.setAccessible(true);
                        } catch (NoSuchMethodException e) {}
                        if (nameMethod == null) {
                            System.err.println("WARNING: Config class " + clzName + " doesnt contain a getConfigTypeName and thus wont be serializable");
                            continue outer;
                        }
                        String name = (String) nameMethod.invoke(null);
                        serializers.computeIfAbsent(clz, GsonConfigSerialization::new)
                                .register(name, (Class) original);
                        System.out.println("Registered " + original.getSimpleName() + " as " + name + " for config type " + clz.getSimpleName());
                        break;
                    }
                }
            } catch (Exception e) {
                System.err.println("Error while setting up config serialization");
                e.printStackTrace();
            }
        }

        var builder = new GsonBuilder();
        for (var entry : serializers.entrySet()) {
            builder.registerTypeAdapterFactory(entry.getValue());
        }

        GSON = builder.create();
    }

    private static List<String> collectAllClasses(String pack) {
        InputStream stream = Serialization.class.getClassLoader()
                .getResourceAsStream(pack.replaceAll("[.]", "/"));
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        return reader.lines().flatMap(inner -> {
            if (inner.endsWith(".class")) {
                return Stream.of(pack + "." + inner.replace(".class", ""));
            } else if (!inner.contains(".")) {
                return collectAllClasses(pack + "." + inner).stream();
            } else {
                return Stream.of();
            }
        }).collect(Collectors.toList());
    }
    private static List<String> collectAllClasses(Path base, String pack) {
        try {
            return Files.list(base.resolve(pack.replaceAll("[.]", "/"))).flatMap(inner -> {
                if (inner.getFileName().toString().endsWith(".class")) {
                    return Stream.of(pack + "." + inner.getFileName().toString().replace(".class", ""));
                } else if (Files.isDirectory(inner)) {
                    return collectAllClasses(base, pack + "." + inner.getFileName()).stream();
                } else {
                    return Stream.of();
                }
            }).collect(Collectors.toList());
        } catch (
                IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void init() {}
}
