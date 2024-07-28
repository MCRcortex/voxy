package me.cortex.voxy.client.core.model;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;

import static me.cortex.voxy.client.core.model.ModelFactory.MODEL_TEXTURE_SIZE;
import static org.lwjgl.opengl.ARBDirectStateAccess.glTextureSubImage2D;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;

public record ModelTextureUpload(int id, int[][] data) {

    public void upload(int textureId) {
        GlStateManager._pixelStore(GlConst.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GlConst.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GlConst.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GlConst.GL_UNPACK_ALIGNMENT, 4);

        int i = 0;
        int X = (this.id&0xFF) * MODEL_TEXTURE_SIZE*3;
        int Y = ((this.id>>8)&0xFF) * MODEL_TEXTURE_SIZE*2;
        for (int face = 0; face < 6; face++) {
            int x = X + (face>>1)*MODEL_TEXTURE_SIZE;
            int y = Y + (face&1)*MODEL_TEXTURE_SIZE;
            for (int mip = 0; mip < 4; mip++) {
                glTextureSubImage2D(id, mip, x >> mip, y >> mip, MODEL_TEXTURE_SIZE >> mip, MODEL_TEXTURE_SIZE >> mip, GL_RGBA, GL_UNSIGNED_BYTE, this.data[i++]);
            }
        }
    }
}
