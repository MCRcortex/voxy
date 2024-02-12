package me.cortex.voxy.common.storage.config;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Stack;

public class ConfigBuildCtx {
    //List of tokens
    public static final String BASE_LEVEL_PATH = "{base_level_path}";

    private final Stack<String> pathStack = new Stack<>();

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
     * Resolves a path with the current build context path
     * @param other path to resolve against
     * @return resolved path
     */
    public String resolvePath(String other) {
        this.pathStack.push(other);
        String path = "";
        for (var part : this.pathStack) {
            path = concatPath(path, part);
        }
        this.pathStack.pop();
        return path;
    }

    /**
     * Substitutes special tokens in the string the configured values
     * @param string the string to substitute
     * @return substituted string
     */
    public String substituteString(String string) {
        //TODO: this
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
