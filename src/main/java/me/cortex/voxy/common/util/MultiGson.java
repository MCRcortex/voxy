package me.cortex.voxy.common.util;

import com.google.gson.*;

import java.lang.reflect.Modifier;
import java.util.*;

public class MultiGson {
    private final List<Class<?>> classes;
    private final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(Modifier.PRIVATE)
            .create();

    private MultiGson(List<Class<?>> classes) {
        this.classes = classes;
    }

    public String toJson(Object... objects) {
        Object[] map = new Object[this.classes.size()];
        if (map.length != objects.length) {
            throw new IllegalArgumentException("Incorrect number of input args");
        }
        for (var obj : objects) {
            if (obj == null) {
                throw new IllegalArgumentException();
            }
            int i = this.classes.indexOf(obj.getClass());
            if (i == -1) {
                throw new IllegalArgumentException("Unknown object class: " + obj.getClass());
            }
            if (map[i] != null) {
                throw new IllegalArgumentException("Duplicate entry classes");
            }
            map[i] = obj;
        }

        var json = new JsonObject();
        for (Object entry : map) {
            GSON.toJsonTree(entry).getAsJsonObject().asMap().forEach((i,j) -> {
                if (json.has(i)) {
                    throw new IllegalArgumentException("Duplicate name inside unified json: " + i);
                }
                json.add(i, j);
            });
        }
        return GSON.toJson(json);
    }

    public Map<Class<?>, Object> fromJson(String json) {
        var obj = GSON.fromJson(json, JsonObject.class);
        LinkedHashMap<Class<?>, Object> objects = new LinkedHashMap<>();
        for (var cls : this.classes) {
            objects.put(cls, GSON.fromJson(obj, cls));
        }
        return objects;
    }

    public static class Builder {
        private final LinkedHashSet<Class<?>> classes = new LinkedHashSet<>();
        public Builder add(Class<?> clz) {
            if (!this.classes.add(clz)) {
                throw new IllegalArgumentException("Class has already been added");
            }
            return this;
        }

        public MultiGson build() {
            return new MultiGson(new ArrayList<>(this.classes));
        }
    }


    private static final class A {
        public int a;
        public int b;
        public int c;
        public int d;
    }

    private static final class B {
        public int q;
        public int e;
        public int g;
        public int l;
    }

    public static void main(String[] args) {
        var gson = new Builder().add(A.class).add(B.class).build();
        var a = new A();
        a.c =11;
        var b = new B();
        System.out.println(gson.fromJson(gson.toJson(a,b)));
    }
}
