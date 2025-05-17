package me.cortex.voxy.client.core.rendering.util;

import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.GlTexture;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.ARBUniformBufferObject.glBindBufferBase;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL45.glBindTextureUnit;

public class LightMapHelper {
    public static void bind(int lightingIndex) {
        glBindSampler(lightingIndex, 0);
        glBindTextureUnit(lightingIndex, ((net.minecraft.client.texture.GlTexture)(MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager().getGlTexture())).getGlId());
    }
}