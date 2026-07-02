package me.cortex.voxy.client.core.gl.shader;


import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class ShaderLoader {
    public static String parse(String id) {
        var src =  "#version 460 core\n";
        src += String.join("\n", ShaderLoadingParser.parseRoot(ResourceLocation.parse(id)));
        return src;
    }


    private static final class ShaderLoadingParser {
        private static final Pattern IMPORT_PATTERN = Pattern.compile("#import <(?<namespace>.*):(?<path>.*)>");
        public static List<String> parseRoot(ResourceLocation id) {
            List<String> out = new ArrayList<>();
            for (var line : toLines(loadShaderAsset(id))) {
                if (line.startsWith("#version")) {
                    continue;
                } else if (line.startsWith("#import")) {
                    var match = IMPORT_PATTERN.matcher(line);
                    if (!match.matches()) throw new IllegalArgumentException("Unknown import: " + line);
                    var iid = ResourceLocation.fromNamespaceAndPath(match.group("namespace"), match.group("path"));
                    out.addAll(parseRoot(iid));
                } else {
                    out.add(line);
                }
            }
            return out;
        }

        private static List<String> toLines(String src) {
            return new BufferedReader(new StringReader(src)).lines().toList();
        }
        private static String loadShaderAsset(ResourceLocation id) {
            String path = String.format("/assets/%s/shaders/%s", id.getNamespace(), id.getPath());
            // cross-mod #imports (e.g. sodium fog.glsl) live in another NeoForge
            // module that this classloader can't reach. fall back to the resource
            // manager so those #includes still resolve.
            try (InputStream in = ShaderLoadingParser.class.getResourceAsStream(path)) {
                if (in != null) {
                    return IOUtils.toString(in, StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to read shader source for " + path, e);
            }
            var rl = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "shaders/" + id.getPath());
            var resource = Minecraft.getInstance().getResourceManager().getResource(rl);
            if (resource.isEmpty()) {
                throw new RuntimeException("Shader not found (classloader + resource manager): " + path);
            }
            try (InputStream in = resource.get().open()) {
                return IOUtils.toString(in, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read shader source for " + path, e);
            }
        }
    }
}
