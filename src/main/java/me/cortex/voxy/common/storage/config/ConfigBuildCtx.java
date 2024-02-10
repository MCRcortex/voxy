package me.cortex.voxy.common.storage.config;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigBuildCtx {
    //List of tokens
    public static final String BASE_LEVEL_PATH = "{base_level_path}";

    //Pushes a path to the BuildCtx path stack so that when resolving with resolvePath it uses the entire path stack
    public ConfigBuildCtx pushPath(String path) {
        return this;
    }

    public ConfigBuildCtx popPath() {
        return this;
    }

    /**
     * Resolves a path with the current build context path
     * @param other path to resolve against
     * @return resolved path
     */
    public String resolvePath(String other) {
        return null;
    }

    /**
     * Substitutes special tokens in the string the configured values
     * @param string the string to substitute
     * @return substituted string
     */
    public String substituteString(String string) {
        //This is e.g. so you can have dbs spread across multiple disks if you want
        return null;
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
