package me.cortex.neovoxy.client.core.rendering.util;

import static org.lwjgl.opengl.GL33.glBindSampler;
import static org.lwjgl.opengl.GL45.glBindTextureUnit;

import me.cortex.neovoxy.client.mixin.minecraft.AccessorLightTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;

public class LightMapHelper {
    public static void bind(int lightingIndex) {
        glBindSampler(lightingIndex, 0);
        LightTexture lightTexture = Minecraft.getInstance().gameRenderer.lightTexture();
        int glId = ((AccessorLightTexture) lightTexture).neovoxy$getLightTexture().getId();
        glBindTextureUnit(lightingIndex, glId);
    }
}
