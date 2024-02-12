package me.cortex.voxy.common.storage.config;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class ConfigBuildCtx {
    //List of tokens
    public static final String BASE_SAVE_PATH = "{base_save_path}";
    public static final String WORLD_IDENTIFIER = "{world_identifier}";
    public static final String DEFAULT_STORAGE_PATH = BASE_SAVE_PATH+"/"+WORLD_IDENTIFIER+"/storage/";


    private final Map<String, String> properties = new HashMap<>();
    private final Stack<String> pathStack = new Stack<>();

    /**
     * Sets a builder property
     * @param property property name
     * @param value property value
     * @return the builder context
     */
    public ConfigBuildCtx setProperty(String property, String value) {
        if (!(property.startsWith("{") && property.endsWith("}"))) {
            throw new IllegalArgumentException("Property name doesnt start with { and end with }");
        }
        this.properties.put(property, value);
        return this;
    }

    /**
     * Pushes a path to the build context so that when resolvePath is called it is with respect to the added path
     * @param path the path to add to the stack
     * @return the build context
     */
    public ConfigBuildCtx pushPath(String path) {
        this.pathStack.push(path);
        return this;
    }

    /**
     * Pops a path from the build context path stack
     * @return the build context
     */
    public ConfigBuildCtx popPath() {
        this.pathStack.pop();
        return this;
    }

    //TODO: FINISH THIS and check and test
    private static String concatPath(String a, String b) {
        if (b.contains("..")) {
            throw new IllegalStateException("Relative resolving not supported");
        }

        if ((!a.isBlank()) && !a.endsWith("/")) {
            a += "/";
        }

        if (b.startsWith("/")) {//Absolute path
            return b;
        }

        if (b.startsWith("./")) {
            b = b.substring(2);
        }

        if (b.startsWith(":", 1)) {//Drive path
            return b;
        }

        return a+b;
    }

    /**
     * Resolves the current path stack recursively
     * @return resolved path
     */
    public String resolvePath() {
        String prev = "";
        String path = "";
        do {
            prev = path;
            path = "";
            for (var part : this.pathStack) {
                path = concatPath(path, part);
            }
        } while (!prev.equals(path));
        return path;
    }

    /**
     * Substitutes special tokens in the string the configured values
     * @param string the string to substitute
     * @return substituted string
     */
    public String substituteString(String string) {
        for (var entry : this.properties.entrySet()) {
            string = string.replace(entry.getKey(), entry.getValue());
        }
        return string;
    }

    /**
     * Ensures that the path provided exists, if not, create it
     * @param path the path to make
     * @return the input
     */
    public String ensurePathExists(String path) {
        try {
            Files.createDirectories(new File(path).toPath());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return path;
    }
}
