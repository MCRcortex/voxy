package me.cortex.voxy.client.core.rendering.hierarchical;

import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.shader.PrintfInjector;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.rendering.geometry.OLD.Gl46HierarchicalViewport;
import me.cortex.voxy.client.core.rendering.HiZBuffer;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.ARBDirectStateAccess.nglClearNamedBufferSubData;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL30.GL_R32UI;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL33.glBindSampler;
import static org.lwjgl.opengl.GL33.glGenSamplers;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.opengl.GL45.glBindTextureUnit;

public class HierarchicalOcclusionRenderer {
    private final HiZBuffer hiz = new HiZBuffer();

    private final int hizSampler = glGenSamplers();

    public final NodeManager nodeManager;
    private final Shader hierarchicalTraversal;
    private final PrintfInjector printf;

    private final GlBuffer nodeQueueA;
    private final GlBuffer nodeQueueB;
    private final GlBuffer uniformBuffer;

    public HierarchicalOcclusionRenderer(INodeInteractor interactor, MeshManager mesh, PrintfInjector printf) {
        this.nodeManager = new NodeManager(interactor, mesh);
        this.nodeQueueA = new GlBuffer(1000000*4+4).zero();
        this.nodeQueueB = new GlBuffer(1000000*4+4).zero();
        this.uniformBuffer = new GlBuffer(1024).zero();
        this.printf = printf;
        this.hierarchicalTraversal = Shader.make(printf)
                .define("IS_DEBUG")
                .add(ShaderType.COMPUTE, "voxy:lod/hierarchical/traversal.comp")
                .compile();
    }

    private void uploadUniform(Gl46HierarchicalViewport viewport) {
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

        MemoryUtil.memPutInt(ptr, NodeManager.REQUEST_QUEUE_SIZE); ptr += 4;
        MemoryUtil.memPutInt(ptr, 1000000); ptr += 4;

        //decendSSS (decend screen space size)
        MemoryUtil.memPutFloat(ptr, 64*64); ptr += 4;
    }

    public void doHierarchicalTraversalSelection(Gl46HierarchicalViewport viewport, int depthBuffer, GlBuffer renderSelectionResult, GlBuffer debugNodeOutput) {
        this.uploadUniform(viewport);
        this.nodeManager.upload();

        {
            int cnt = this.nodeManager.rootPos2Id.size();
            long ptr = UploadStream.INSTANCE.upload(this.nodeQueueA, 0, 4+cnt*4L);
            MemoryUtil.memPutInt(ptr, cnt); ptr += 4;
            for (int i : this.nodeManager.rootPos2Id.values()) {
                MemoryUtil.memPutInt(ptr, i); ptr += 4;
            }
        }


        UploadStream.INSTANCE.commit();

        //FIXME: need to have the hiz respect the stencil mask aswell to mask away normal terrain, (much increase perf)

        //Make hiz
        this.hiz.buildMipChain(depthBuffer, viewport.width, viewport.height);
        glMemoryBarrier(GL_PIXEL_BUFFER_BARRIER_BIT);
        this.hierarchicalTraversal.bind();

        //Clear the render counter
        nglClearNamedBufferSubData(renderSelectionResult.id, GL_R32UI, 0, 4, GL_RED_INTEGER, GL_UNSIGNED_INT, 0);
        nglClearNamedBufferSubData(debugNodeOutput.id, GL_R32UI, 0, 4, GL_RED_INTEGER, GL_UNSIGNED_INT, 0);

        {
            glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniformBuffer.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, this.nodeManager.nodeBuffer.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, this.nodeQueueA.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, this.nodeManager.requestQueue.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, renderSelectionResult.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 6, this.nodeQueueB.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 7, debugNodeOutput.id);

            //Bind the hiz buffer
            glBindSampler(0, this.hizSampler);
            glBindTextureUnit(0, this.hiz.getHizTextureId());
        }
        this.printf.bind();
        {
            //Dispatch hierarchies
            nglClearNamedBufferSubData(this.nodeQueueB.id, GL_R32UI, 0, 4, GL_RED_INTEGER, GL_UNSIGNED_INT, 0);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, this.nodeQueueA.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 6, this.nodeQueueB.id);
            glDispatchCompute(21*21*2,1,1);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

            nglClearNamedBufferSubData(this.nodeQueueA.id, GL_R32UI, 0, 4, GL_RED_INTEGER, GL_UNSIGNED_INT, 0);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, this.nodeQueueB.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 6, this.nodeQueueA.id);
            glDispatchCompute(21*21*8,1,1);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

            nglClearNamedBufferSubData(this.nodeQueueB.id, GL_R32UI, 0, 4, GL_RED_INTEGER, GL_UNSIGNED_INT, 0);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, this.nodeQueueA.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 6, this.nodeQueueB.id);
            glDispatchCompute(21*21*32,1,1);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

            nglClearNamedBufferSubData(this.nodeQueueA.id, GL_R32UI, 0, 4, GL_RED_INTEGER, GL_UNSIGNED_INT, 0);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, this.nodeQueueB.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 6, this.nodeQueueA.id);
            glDispatchCompute(21*21*128,1,1);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

            nglClearNamedBufferSubData(this.nodeQueueB.id, GL_R32UI, 0, 4, GL_RED_INTEGER, GL_UNSIGNED_INT, 0);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, this.nodeQueueA.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 6, this.nodeQueueB.id);
            glDispatchCompute(21*21*8,1,1);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        }

        glBindSampler(0, 0);
        glBindTextureUnit(0, 0);
        this.nodeManager.download();
    }

    public void free() {
        this.nodeQueueA.free();
        this.nodeQueueB.free();
        this.hiz.free();
        this.nodeManager.free();
        glDeleteSamplers(this.hizSampler);
    }

    public GlBuffer getNodeDataBuffer() {
        return this.nodeManager.nodeBuffer;
    }
}
