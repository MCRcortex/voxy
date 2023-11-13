package me.cortex.voxelmon.core.gl.shader;

public interface IShaderProcessor {
    String process(ShaderType type, String source);
}
