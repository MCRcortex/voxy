package me.cortex.voxy.client.core.rendering.util;

import static org.lwjgl.opengl.GL33.glBindSampler;
import static me.cortex.voxy.client.core.gl.GLCompat.bindTextureUnit;

import net.minecraft.client.Minecraft;

public class LightMapHelper {
    public static void bind(int lightingIndex) {
        glBindSampler(lightingIndex, 0);
        bindTextureUnit(lightingIndex, ((com.mojang.blaze3d.opengl.GlTexture)(Minecraft.getInstance().gameRenderer.lightTexture().getTextureView().texture())).glId());
    }
}
