package me.cortex.voxy.common.config;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

public class ConfigBuildCtx {
    //List of tokens
    public static final String BASE_SAVE_PATH = "{base_save_path}";
    public static final String WORLD_IDENTIFIER = "{world_identifier}";
    public static final String DEFAULT_STORAGE_PATH = BASE_SAVE_PATH+"/"+WORLD_IDENTIFIER+"/storage/";
    private static final Set<String> ALLOWED_PROPERTIES = new HashSet<>(List.of(BASE_SAVE_PATH, WORLD_IDENTIFIER));

    private final Map<String, String> properties = new HashMap<>();
    private final Stack<String> pathStack = new Stack<>();

    /**
     * Sets a builder property
     * @param property property name
     * @param value property value
     * @return the builder context
     */
    public ConfigBuildCtx setProperty(String property, String value) {
        if (!ALLOWED_PROPERTIES.contains(property)) {
            throw new IllegalArgumentException("Property not within the set of allowed properties");
        }
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
     * Resolves the current path stack recursively and then resolves all the properties
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
        return this.resolveString(path);
    }

    /**
     * Continuously substitutes all the property tokens in the string with the property values
     * @param string the string to resolve
     * @param disallowProperties properties not to apply (e.g. BASE_SAVE_PATH)
     * @return resolved string
     */
    public String resolveString(String string, String... disallowProperties) {
        Set<String> disallowSet = new HashSet<>(List.of(disallowProperties));
        String cstr = null;
        while (!string.equals(cstr)) {
            cstr = string;
            for (var entry : this.properties.entrySet()) {
                if (disallowSet.contains(entry.getValue())) continue;
                string = string.replace(entry.getKey(), entry.getValue());
            }
        }
        return string;
    }

    /**
     * Gets the raw property result requested or empty string if it doesnt exist
     * @param property the property to get
     * @return the raw property value
     */
    public String getRawProperty(String property) {
        return this.properties.getOrDefault(property, "");
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

    /**
     * Copies the current build context
     * @return a copy of the context
     */
    public ConfigBuildCtx copy() {
        var clone = new ConfigBuildCtx();
        clone.properties.clear();
        clone.properties.putAll(this.properties);
        clone.pathStack.clear();
        clone.pathStack.addAll(this.pathStack);
        return clone;
    }
}
