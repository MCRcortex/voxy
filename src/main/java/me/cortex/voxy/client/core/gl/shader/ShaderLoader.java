package me.cortex.voxy.client.core.gl.shader;


import net.caffeinemc.mods.sodium.client.gl.shader.ShaderConstants;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderParser;

public class ShaderLoader {
    public static String parse(String id) {
        String src = "#version 460 core\n"+ShaderParser.parseShader("\n#import <" + id + ">\n//beans", ShaderConstants.builder().build()).src().replaceAll("\r\n", "\n").replaceFirst("\n#version .+\n", "\n");
        // Strip printf calls — same substitution as PrintfDebugUtil.PRINTF_processor
        // when shader-printf debugging is off (the default). Without this, the
        // runtime SPIRV/MSL compile path on Metal/Vulkan fails because glslang
        // rejects `printf(string-literal, ...)` unless GL_EXT_debug_printf is
        // requested, and Voxy's debug helper functions in node.glsl / queue.glsl
        // declare them unconditionally. The opt-in
        // -Dvoxy.enableShaderDebugPrintf=true path keeps the printf calls so
        // PrintfInjector can transform them downstream.
        if (!me.cortex.voxy.client.core.rendering.util.PrintfDebugUtil.ENABLE_PRINTF_DEBUGGING) {
            src = src.replace("printf", "//printf");
        }
        return src;
        //return me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader.getShaderSource(new Identifier(id));
    }

    /**
     * Parse + strip {@code printf(...)} calls so the resulting source compiles
     * through glslang/shaderc (which rejects printf without
     * GL_EXT_debug_printf). The legacy
     * {@code Shader.makeAuto(PrintfDebugUtil.PRINTF_processor)} flow applied
     * this strip automatically; M9 callers that bypass Shader.Builder and
     * feed source straight to {@code createComputePipeline}/
     * {@code createGraphicsPipeline} use this method instead.
     *
     * Same text substitution as PrintfDebugUtil.PRINTF_processor when shader
     * printf debugging is off (the default): every {@code printf} becomes
     * {@code //printf}, commenting the rest of the line.
     */
    public static String parseAndStripPrintf(String id) {
        return parse(id).replace("printf", "//printf");
    }
}
