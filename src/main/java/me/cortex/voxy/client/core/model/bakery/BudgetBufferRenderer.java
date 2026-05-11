package me.cortex.voxy.client.core.model.bakery;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import me.cortex.voxy.client.core.gl.GlGraphicsPipeline;
import me.cortex.voxy.client.core.gl.shader.ShaderLoader;
import me.cortex.voxy.client.core.gpu.GraphicsPipelineDesc;
import me.cortex.voxy.client.core.gpu.IGpuBuffer;
import me.cortex.voxy.client.core.gpu.IGpuPipeline;
import me.cortex.voxy.client.core.gpu.IGpuVertexArray;
import me.cortex.voxy.client.core.gpu.PipelineState;
import me.cortex.voxy.client.core.gpu.RenderBackendFactory;
import me.cortex.voxy.client.core.gpu.VertexLayout;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL11C.GL_RGBA8;
import static org.lwjgl.opengl.GL15C.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.glBindBufferRange;
import static org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL33.glBindSampler;
import static org.lwjgl.opengl.GL45.*;

/**
 * Static helper that streams a quad mesh + texture into the bakery framebuffer.
 *
 * M9 status: the shader pipeline now flows through
 * {@link me.cortex.voxy.client.core.gpu.RenderBackend#createGraphicsPipeline},
 * so the bakery shaders compile cleanly on Metal/Vulkan even though the
 * runtime bind/draw path stays raw GL — {@link ModelTextureBakery} (this
 * helper's caller) owns the framebuffer and viewport setup, and itself
 * sits on the GL-only bakery codepath. The matrix uniform that used to be
 * a location-based {@code glUniformMatrix4fv} now lands in a UBO push
 * block at {@code PUSH_BINDING} via the new {@link #render} flow.
 */
public class BudgetBufferRenderer {
    public static final int VERTEX_FORMAT_SIZE = 24;

    /** UBO binding for position_tex.vsh's `Push { mat4 transform; }`. */
    private static final int PUSH_BINDING = 14;

    private static final IGpuPipeline bakeryPipeline;
    /** Cached GL program id for the raw glUseProgram path. 0 on non-GL. */
    private static final int bakeryGlProgram;
    /** Lazy UBO used to push the per-draw matrix. */
    private static int pushUbo;
    private static long pushUboCapacity;

    static {
        bakeryPipeline = RenderBackendFactory.get().createGraphicsPipeline(new GraphicsPipelineDesc(
                ShaderLoader.parse("voxy:bakery/position_tex.vsh"),
                ShaderLoader.parse("voxy:bakery/position_tex.fsh"),
                java.util.Map.of("PUSH_BINDING", Integer.toString(PUSH_BINDING)),
                null, null,           // no MSL — runtime compiler produces on Metal
                null, null,           // no SPIRV — runtime compiler produces on Vulkan
                GL_RGBA8,             // color format — actual FBO is owned by ModelTextureBakery
                VertexLayout.EMPTY,   // vertex layout managed by the legacy IGpuVertexArray below
                PipelineState.DEFAULT,
                "BudgetBufferRenderer.bakery"));
        bakeryGlProgram = (bakeryPipeline instanceof GlGraphicsPipeline gp) ? gp.program() : 0;
    }


    public static void init(){}
    private static final IGpuBuffer indexBuffer;
    static {
        indexBuffer = RenderBackendFactory.get().createBuffer(3*2*2*4096);
        // M9 transitional: copying MC's sequential quad index buffer here only
        // works on the GL backend because the source handle comes from
        // com.mojang.blaze3d.opengl.GlBuffer (GL-only) and the destination
        // is bound to a GL function. On Metal/Vulkan the destination buffer
        // exists but is left zeroed; the bakery codepath that consumes it
        // hasn't been migrated yet, so this stays unused until M9 reaches
        // BudgetBufferRenderer/the bakery renderer proper.
        if (RenderBackendFactory.get().getType() == me.cortex.voxy.client.core.gpu.BackendType.OPENGL) {
            var i = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
            int id = ((com.mojang.blaze3d.opengl.GlBuffer) i.getBuffer(4096*3*2)).handle;
            if (i.type() != VertexFormat.IndexType.SHORT) {
                throw new IllegalStateException();
            }
            glCopyNamedBufferSubData(id, indexBuffer.id(), 0, 0, 3*2*2*4096);
        }
    }

    private static final int STRIDE = 24;
    private static final IGpuVertexArray VA = RenderBackendFactory.get().createVertexArray()
            .setStride(STRIDE)
            .setF(0, GL_FLOAT, 4, 0)//pos, metadata
            .setF(1, GL_FLOAT, 2, 4 * 4)//UV
            .bindElementBuffer(indexBuffer.id());

    private static IGpuBuffer immediateBuffer;
    private static int quadCount;
    public static void drawFast(MeshData buffer, GpuTexture tex, Matrix4f matrix) {
        if (buffer.drawState().mode() != VertexFormat.Mode.QUADS) {
            throw new IllegalStateException("Fast only supports quads");
        }

        var buff = buffer.vertexBuffer();
        int size = buff.remaining();
        if (size%STRIDE != 0) throw new IllegalStateException();
        size /= STRIDE;
        if (size%4 != 0) throw new IllegalStateException();
        size /= 4;
        setup(MemoryUtil.memAddress(buff), size, ((com.mojang.blaze3d.opengl.GlTexture)tex).glId());
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
            immediateBuffer = RenderBackendFactory.get().createBuffer(size*2L);//This also accounts for when immediateBuffer == null
            VA.bindBuffer(immediateBuffer.id());
        }
        long ptr = UploadStream.INSTANCE.upload(immediateBuffer, 0, size);
        MemoryUtil.memCopy(dataPtr, ptr, size);
        UploadStream.INSTANCE.commit();

        // M9 transitional: bakeryPipeline drives shader compilation cross-backend
        // but bind/draw stays raw GL because ModelTextureBakery owns the
        // framebuffer/viewport setup outside any RenderEncoder. The pipeline-cached
        // program id is 0 on non-GL backends (so the bind is a no-op there).
        if (bakeryGlProgram != 0) glUseProgram(bakeryGlProgram);
        VA.bind();
        glMemoryBarrier(GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);
        glBindSampler(0, 0);
        me.cortex.voxy.client.core.gl.GLCompat.bindTextureUnit(0, texId);
    }

    public static void render(Matrix4f matrix) {
        // Push the transform matrix (64 bytes) into the Push UBO at PUSH_BINDING.
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long addr = stack.nmalloc(64);
            matrix.getToAddress(addr);
            pushMatrix(addr, 64);
        }
        glDrawElements(GL_TRIANGLES, quadCount * 2 * 3, GL_UNSIGNED_SHORT, 0);
    }

    private static void pushMatrix(long addr, int size) {
        long needed = (size + 255) & ~255L;
        if (pushUbo == 0) {
            pushUbo = glCreateBuffers();
            pushUboCapacity = Math.max(needed, 256);
            glNamedBufferData(pushUbo, pushUboCapacity, GL_DYNAMIC_DRAW);
        } else if (needed > pushUboCapacity) {
            pushUboCapacity = needed;
            glNamedBufferData(pushUbo, pushUboCapacity, GL_DYNAMIC_DRAW);
        }
        nglNamedBufferSubData(pushUbo, 0, size, addr);
        glBindBufferRange(GL_UNIFORM_BUFFER, PUSH_BINDING, pushUbo, 0, size);
    }

    public static void shutdown() {
        if (pushUbo != 0) {
            glDeleteBuffers(pushUbo);
            pushUbo = 0;
        }
        bakeryPipeline.close();
    }
}
