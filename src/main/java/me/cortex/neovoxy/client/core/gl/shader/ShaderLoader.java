package me.cortex.neovoxy.client.core.gl.shader;


import net.caffeinemc.mods.sodium.client.gl.shader.ShaderConstants;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderParser;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NeoForge-compatible shader loader for NeoVoxy.
 *
 * On Fabric, Sodium's ShaderLoader.getShaderSource() uses a flat classloader that can
 * access all mod resources. On NeoForge, each mod has an isolated classloader, so
 * Sodium's classloader cannot access NeoVoxy's shader resources.
 *
 * This loader bypasses Sodium's resource loading and uses NeoVoxy's own classloader.
 *
 * Upstream reference: original Voxy / NeoVoxy shader loader.
 * See: src/main/java/me/cortex/neovoxy/client/core/gl/shader/ShaderLoader.java
 */
public class ShaderLoader {
    private static final Pattern IMPORT_PATTERN = Pattern.compile("#import <(?<namespace>.*):(?<path>.*)>");

    /**
     * Parse and load a shader, matching upstream NeoVoxy behavior.
     *
     * Upstream code:
     *   return "#version 460 core\n" + ShaderParser.parseShader(
     *       "\n#import <" + id + ">\n//beans", ShaderConstants.builder().build()
     *   ).src().replaceAll("\r\n", "\n").replaceFirst("\n#version .+\n", "\n");
     *
     * The key is the leading "\n" before #import - this ensures the regex
     * "\n#version .+\n" can match the #version directive in the loaded shader.
     */
    public static String parse(String id) {
        // Load shader source using NeoVoxy's classloader (NeoForge classloader isolation fix)
        String shaderSource = getShaderSource(id);

        // Process any nested #import directives recursively
        shaderSource = processImports(shaderSource);

        // Match upstream format: "\n" + content + "\n//beans"
        // The leading \n is critical for the regex to work
        String processed = "\n" + shaderSource + "\n//beans";

        // Apply Sodium's shader constants processing (handles #define etc.)
        processed = ShaderParser.parseShader(processed, ShaderConstants.builder().build()).src();

        // Normalize line endings and strip original #version (upstream behavior)
        processed = processed.replaceAll("\r\n", "\n");
        processed = processed.replaceFirst("\n#version .+\n", "\n");

        // Prepend our target GLSL version
        return "#version 460 core\n" + processed;
    }

    /**
     * Load shader source using NeoVoxy's classloader.
     * Path format: "namespace:path" -> "/assets/{namespace}/shaders/{path}"
     */
    private static String getShaderSource(String id) {
        String[] parts = id.split(":", 2);
        String namespace = parts.length > 1 ? parts[0] : "neovoxy";
        String path = parts.length > 1 ? parts[1] : parts[0];

        String resourcePath = String.format("/assets/%s/shaders/%s", namespace, path);

        try (InputStream in = ShaderLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new RuntimeException("Shader not found: " + resourcePath + " (id=" + id + ")");
            }
            return IOUtils.toString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read shader source: " + resourcePath, e);
        }
    }

    /**
     * Process #import directives recursively, loading from NeoVoxy's resources.
     */
    private static String processImports(String source) {
        StringBuilder result = new StringBuilder();
        for (String line : source.split("\n")) {
            if (line.trim().startsWith("#import")) {
                Matcher matcher = IMPORT_PATTERN.matcher(line.trim());
                if (matcher.matches()) {
                    String namespace = matcher.group("namespace");
                    String path = matcher.group("path");
                    String importId = namespace + ":" + path;
                    String importedSource = getShaderSource(importId);
                    result.append(processImports(importedSource));
                } else {
                    result.append(line);
                }
            } else {
                result.append(line);
            }
            result.append("\n");
        }
        return result.toString();
    }
}
