package me.cortex.voxy.client.core.gl.shader;

import java.util.regex.Pattern;

public class GenericsProcessor implements IShaderProcessor {
    private static final Pattern GENERIC_DEFINE = Pattern.compile("#defineGen (?<name>[A-Za-z0-9]+)<(?<generic>[A-Za-z0-9]*)>");
    private static final Pattern GENERIC_USE = Pattern.compile("(?<type>[A-Za-z0-9]+)<(?<generic>[A-Za-z0-9]*)>");
    @Override
    public String process(ShaderType type, String source) {
        return null;
    }
}
