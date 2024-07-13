package me.cortex.voxy.client.core.rendering.hierarchical;

import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.shader.PrintfInjector;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
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
    private PrintfInjector printf = new PrintfInjector(100000, 10, System.out::println);

    private final MeshManager meshManager = new MeshManager();
    private final NodeManager nodeManager = new NodeManager(new INodeInteractor() {
        @Override
        public void watchUpdates(long pos) {

        }

        @Override
        public void unwatchUpdates(long pos) {

        }

        @Override
        public void requestMesh(long pos) {

        }

        @Override
        public void setMeshUpdateCallback(Consumer<BuiltSection> mesh) {

        }
    }, this.meshManager);

    private final HiZBuffer hiz = new HiZBuffer();

    private final int hizSampler = glGenSamplers();

    private final Shader hierarchicalTraversal = Shader.make(this.printf)
            .add(ShaderType.COMPUTE, "voxy:lod/hierarchical/traversal.comp")
            .compile();

    private final GlBuffer nodeQueue;
    private final GlBuffer renderQueue;
    private final GlBuffer uniformBuffer;

    public HierarchicalOcclusionRenderer() {
        this.nodeQueue = new GlBuffer(1000000*4+4).zero();
        this.renderQueue = new GlBuffer(1000000*4+4).zero();
        this.uniformBuffer = new GlBuffer(1024).zero();
    }

    private void uploadUniform() {
        long ptr = UploadStream.INSTANCE.upload(this.uniformBuffer, 0, 1024);

    }

    private void doHierarchicalTraversal(int depthBuffer, int width, int height) {
        this.uploadUniform();
        this.nodeManager.upload();
        //Make hiz
        this.hiz.buildMipChain(depthBuffer, width, height);
        glMemoryBarrier(GL_PIXEL_BUFFER_BARRIER_BIT);
        this.hierarchicalTraversal.bind();

        {
            glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniformBuffer.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, this.nodeManager.nodeBuffer.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, this.nodeQueue.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, this.nodeManager.requestQueue.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, this.renderQueue.id);

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

    public void render(int depthBuffer, int width, int height) {
        this.doHierarchicalTraversal(depthBuffer, width, height);


        this.printf.download();
    }

    public void free() {
        this.nodeQueue.free();
        this.renderQueue.free();
        this.printf.free();
        this.hiz.free();
        this.nodeManager.free();
        this.meshManager.free();
        glDeleteSamplers(this.hizSampler);
    }
}
