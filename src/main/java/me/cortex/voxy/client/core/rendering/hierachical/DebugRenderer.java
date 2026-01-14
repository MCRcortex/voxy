package me.cortex.voxy.client.core.rendering.hierachical;

import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.GlVertexArray;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.rendering.Viewport;
import me.cortex.voxy.client.core.rendering.util.SharedIndexBuffer;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL40.glDrawElementsIndirect;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL43.*;

public class DebugRenderer {
    private final Shader debugShader = Shader.make()
            .add(ShaderType.VERTEX, "voxy:lod/hierarchical/debug/node_outline.vert")
            .add(ShaderType.FRAGMENT, "voxy:lod/hierarchical/debug/frag.frag")
            .compile();
    private final Shader setupShader = Shader.make()
            .add(ShaderType.COMPUTE, "voxy:lod/hierarchical/debug/setup.comp")
            .compile();

    private final GlBuffer uniformBuffer = new GlBuffer(1024).zero();
    private final GlBuffer drawBuffer = new GlBuffer(1024).zero();

    private void uploadUniform(Viewport<?> viewport) {
        long ptr = UploadStream.INSTANCE.upload(this.uniformBuffer, 0, 1024);
        int sx = MathHelper.floor(viewport.cameraX)>>5;
        int sy = MathHelper.floor(viewport.cameraY)>>5;
        int sz = MathHelper.floor(viewport.cameraZ)>>5;

        new Matrix4f(viewport.projection).mul(viewport.modelView).getToAddress(ptr); ptr += 4*4*4;

        MemoryUtil.memPutInt(ptr, sx); ptr += 4;
        MemoryUtil.memPutInt(ptr, sy); ptr += 4;
        MemoryUtil.memPutInt(ptr, sz); ptr += 4;
        MemoryUtil.memPutInt(ptr, viewport.width); ptr += 4;

        var innerTranslation = new Vector3f((float) (viewport.cameraX-(sx<<5)), (float) (viewport.cameraY-(sy<<5)), (float) (viewport.cameraZ-(sz<<5)));
        innerTranslation.getToAddress(ptr); ptr += 4*3;

        MemoryUtil.memPutInt(ptr, viewport.height); ptr += 4;
    }

    public void render(Viewport<?> viewport, GlBuffer nodeData, GlBuffer nodeList) {
        this.uploadUniform(viewport);
        UploadStream.INSTANCE.commit();

        this.setupShader.bind();
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, this.drawBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, nodeList.id);
        glDispatchCompute(1,1,1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT|GL_COMMAND_BARRIER_BIT);

        glEnable(GL_DEPTH_TEST);
        this.debugShader.bind();
        glBindVertexArray(GlVertexArray.STATIC_VAO);
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, this.drawBuffer.id);
        GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, SharedIndexBuffer.INSTANCE_BYTE.id());
        glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniformBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, nodeData.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, nodeList.id);
        glDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_BYTE, 0);
    }

    public void free() {
        this.drawBuffer.free();
        this.uniformBuffer.free();
        this.debugShader.free();
        this.setupShader.free();
    }
}
