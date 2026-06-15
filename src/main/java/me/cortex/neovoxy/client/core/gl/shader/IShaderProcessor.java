package me.cortex.neovoxy.client.core.gl.shader;

public interface IShaderProcessor {
    String process(ShaderType type, String source);
}
