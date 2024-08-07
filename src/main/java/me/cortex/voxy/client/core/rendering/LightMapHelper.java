package me.cortex.voxy.client.core.rendering;

import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.ARBUniformBufferObject.glBindBufferBase;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;

public class LightMapHelper {
    private static final GlBuffer LIGHT_MAP_BUFFER = new GlBuffer(256*4);
    public static void tickLightmap() {
        long upload = UploadStream.INSTANCE.upload(LIGHT_MAP_BUFFER, 0, 256*4);
        var lmt = MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager().texture.getImage();
        for (int light = 0; light < 256; light++) {
            int x = light&0xF;
            int y = ((light>>4)&0xF);
            int sample = lmt.getColor(x,y);
            sample = ((sample&0xFF0000)>>16)|(sample&0xFF00)|((sample&0xFF)<<16);
            MemoryUtil.memPutInt(upload + (((x<<4)|(y))*4), sample|(0xFF<<28));
        }
    }

    public static void bind(int lightingBufferIndex) {
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, lightingBufferIndex, LIGHT_MAP_BUFFER.id);
    }

}
