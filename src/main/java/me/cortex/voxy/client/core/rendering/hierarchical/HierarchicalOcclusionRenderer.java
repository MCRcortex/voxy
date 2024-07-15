package me.cortex.voxy.client.core.rendering.hierarchical;

import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.shader.PrintfInjector;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.rendering.Gl46HierarchicalViewport;
import me.cortex.voxy.client.core.rendering.HiZBuffer;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.hierarchical.INodeInteractor;
import me.cortex.voxy.client.core.rendering.hierarchical.MeshManager;
import me.cortex.voxy.client.core.rendering.hierarchical.NodeManager;
import me.cortex.voxy.client.core.rendering.util.UploadStream;

import java.util.function.Consumer;

import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL33.glBindSampler;
import static org.lwjgl.opengl.GL33.glGenSamplers;
import static org.lwjgl.opengl.GL42C.*;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43.glDispatchCompute;
import static org.lwjgl.opengl.GL45.glBindTextureUnit;

public class HierarchicalOcclusionRenderer {
    private final HiZBuffer hiz = new HiZBuffer();

    private final int hizSampler = glGenSamplers();

    private final NodeManager nodeManager;
    private final Shader hierarchicalTraversal;
    private final PrintfInjector printf;

    private final GlBuffer nodeQueue;
    private final GlBuffer uniformBuffer;

    public HierarchicalOcclusionRenderer(INodeInteractor interactor, MeshManager mesh, PrintfInjector printf) {
        this.nodeManager = new NodeManager(interactor, mesh);
        this.nodeQueue = new GlBuffer(1000000*4+4).zero();
        this.uniformBuffer = new GlBuffer(1024).zero();
        this.printf = printf;
        this.hierarchicalTraversal = Shader.make(printf)
                .add(ShaderType.COMPUTE, "voxy:lod/hierarchical/traversal.comp")
                .compile();
    }

    private void uploadUniform() {
        long ptr = UploadStream.INSTANCE.upload(this.uniformBuffer, 0, 1024);

    }

    public void doHierarchicalTraversalSelection(Gl46HierarchicalViewport viewport, int depthBuffer, GlBuffer renderSelectionResult) {
        this.uploadUniform();
        this.nodeManager.upload();

        //Make hiz
        this.hiz.buildMipChain(depthBuffer, viewport.width, viewport.height);
        glMemoryBarrier(GL_PIXEL_BUFFER_BARRIER_BIT);
        this.hierarchicalTraversal.bind();

        {
            glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniformBuffer.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, this.nodeManager.nodeBuffer.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, this.nodeQueue.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, this.nodeManager.requestQueue.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, renderSelectionResult.id);

            //Bind the hiz buffer
            glBindSampler(0, this.hizSampler);
            glBindTextureUnit(0, this.hiz.getHizTextureId());
        }
        this.printf.bind();
        {
            //Dispatch hierarchies
            glDispatchCompute(1,1,1);
        }

        this.nodeManager.download();
    }

    public void free() {
        this.nodeQueue.free();
        this.hiz.free();
        this.nodeManager.free();
        glDeleteSamplers(this.hizSampler);
    }
}
