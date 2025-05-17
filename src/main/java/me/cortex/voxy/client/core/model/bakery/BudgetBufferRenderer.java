package me.cortex.voxy.client.core.model.bakery;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.VertexFormat;
import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.GlVertexArray;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import net.minecraft.client.gl.GlGpuBuffer;
import net.minecraft.client.render.BuiltBuffer;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL33.glBindSampler;
import static org.lwjgl.opengl.GL45.*;

public class BudgetBufferRenderer {
    public static final int VERTEX_FORMAT_SIZE = 24;

    private static final Shader bakeryShader = Shader.make()
            .add(ShaderType.VERTEX, "voxy:bakery/position_tex.vsh")
            .add(ShaderType.FRAGMENT, "voxy:bakery/position_tex.fsh")
            .compile();


    private static final GlBuffer indexBuffer;
    static {
        var i = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
        int id = ((GlGpuBuffer) i.getIndexBuffer(4096*3*2)).id;
        if (i.getIndexType() != VertexFormat.IndexType.SHORT) {
            throw new IllegalStateException();
        }
        indexBuffer = new GlBuffer(3*2*2*4096);
        glCopyNamedBufferSubData(id, indexBuffer.id, 0, 0, 3*2*2*4096);
    }

    private static final int STRIDE = 24;
    private static final GlVertexArray VA = new GlVertexArray()
            .setStride(STRIDE)
            .setF(0, GL_FLOAT, 4, 0)//pos, metadata
            .setF(1, GL_FLOAT, 2, 4 * 4)//UV
            .bindElementBuffer(indexBuffer.id);

    private static GlBuffer immediateBuffer;
    private static int quadCount;
    public static void drawFast(BuiltBuffer buffer, GpuTexture tex, Matrix4f matrix) {
        if (buffer.getDrawParameters().mode() != VertexFormat.DrawMode.QUADS) {
            throw new IllegalStateException("Fast only supports quads");
        }

        var buff = buffer.getBuffer();
        int size = buff.remaining();
        if (size%STRIDE != 0) throw new IllegalStateException();
        size /= STRIDE;
        if (size%4 != 0) throw new IllegalStateException();
        size /= 4;
        setup(MemoryUtil.memAddress(buff), size, ((net.minecraft.client.texture.GlTexture)tex).getGlId());
        buffer.close();

        render(matrix);
    }

    public static void setup(long dataPtr, int quads, int texId) {
        if (quads == 0) {
            throw new IllegalStateException();
        }

        quadCount = quads;

        long size = quads * 4L * STRIDE;
        if (immediateBuffer == null || immediateBuffer.size()<size) {
            if (immediateBuffer != null) {
                immediateBuffer.free();
            }
            immediateBuffer = new GlBuffer(size*2L);//This also accounts for when immediateBuffer == null
            VA.bindBuffer(immediateBuffer.id);
        }
        long ptr = UploadStream.INSTANCE.upload(immediateBuffer, 0, size);
        MemoryUtil.memCopy(dataPtr, ptr, size);
        UploadStream.INSTANCE.commit();

        bakeryShader.bind();
        VA.bind();
        glMemoryBarrier(GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);
        glBindSampler(0, 0);
        glBindTextureUnit(0, texId);
    }

    public static void render(Matrix4f matrix) {
        glUniformMatrix4fv(1, false, matrix.get(new float[16]));
        glDrawElements(GL_TRIANGLES, quadCount * 2 * 3, GL_UNSIGNED_SHORT, 0);
    }
}
