package me.cortex.voxelmon.core.gl.shader;

import me.jellysquid.mods.sodium.client.gl.shader.ShaderConstants;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderParser;
import net.minecraft.util.Identifier;

public class ShaderLoader {
    public static String parse(String id) {
        return ShaderParser.parseShader("#import <" + id + ">", ShaderConstants.builder().build());
        //return me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader.getShaderSource(new Identifier(id));
    }
}
