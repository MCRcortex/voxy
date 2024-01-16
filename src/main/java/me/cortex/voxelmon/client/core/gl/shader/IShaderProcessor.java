package me.cortex.voxelmon.client.core.gl.shader;

public interface IShaderProcessor {
    String process(ShaderType type, String source);
}
