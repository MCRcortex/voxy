package me.cortex.voxy.common.util;

import me.cortex.voxy.common.config.Serialization;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ClassFinder {
    public static List<String> findClasses(String basePackages) {
        return findClasses(List.of(basePackages));
    }

    public static List<String> findClasses(List<String> basePackages) {
        var paths = FabricLoader.getInstance().getModContainer("voxy").get().getRootPaths();
        Set<String> set = new LinkedHashSet<>();
        for (var basePackage : basePackages) {
            set.addAll(collectAllClasses(basePackage));
            for (var path : paths) {
                set.addAll(collectAllClasses(path, basePackage));
            }
        }
        return new ArrayList<>(set);
    }

    private static List<String> collectAllClasses(String pack) {
        try {
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
        } catch (Exception e) {
            System.err.println("Failed to collect classes in package: " + pack);
            return List.of();
        }
    }

    private static List<String> collectAllClasses(Path base, String pack) {
        if (!Files.exists(base.resolve(pack.replaceAll("[.]", "/")))) {
            return List.of();
        }
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
}
