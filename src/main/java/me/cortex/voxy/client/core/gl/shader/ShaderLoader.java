package me.cortex.voxy.client.core.gl.shader;


import net.caffeinemc.mods.sodium.client.gl.shader.ShaderConstants;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderParser;

public class ShaderLoader {
    public static String parse(String id) {
        return "#version 460 core\n"+ShaderParser.parseShader("\n#import <" + id + ">\n//beans", ShaderConstants.builder().build()).replaceAll("\r\n", "\n").replaceFirst("\n#version .+\n", "\n");
        //return me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader.getShaderSource(new Identifier(id));
    }
}
