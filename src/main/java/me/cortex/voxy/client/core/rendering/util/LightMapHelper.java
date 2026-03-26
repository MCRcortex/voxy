package me.cortex.voxy.client.core.rendering.util;

import static org.lwjgl.opengl.GL33.glBindSampler;
import static org.lwjgl.opengl.GL45.glBindTextureUnit;

import net.minecraft.client.Minecraft;

public class LightMapHelper {
    public static void bind(int lightingIndex) {
        glBindSampler(lightingIndex, 0);
        glBindTextureUnit(lightingIndex, getLightmapTextureId());
    }

    public static int getLightmapTextureId() {
        return ((com.mojang.blaze3d.opengl.GlTexture)(Minecraft.getInstance().gameRenderer.levelLightmap().texture())).glId();
    }
}