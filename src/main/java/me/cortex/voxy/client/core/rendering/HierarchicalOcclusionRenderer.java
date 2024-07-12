package me.cortex.voxy.client.core.rendering;

import me.cortex.voxy.client.core.gl.shader.PrintfInjector;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.rendering.hierarchical.NodeManager2;

import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL33.glBindSampler;
import static org.lwjgl.opengl.GL33.glGenSamplers;
import static org.lwjgl.opengl.GL42C.*;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL44.GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT;
import static org.lwjgl.opengl.GL45.glBindTextureUnit;

public class HierarchicalOcclusionRenderer {
    private PrintfInjector printf = new PrintfInjector(100000, 10, System.out::println);

    private final NodeManager2 nodeManager = new NodeManager2(null, null);
    private final HiZBuffer hiz = new HiZBuffer();
    private final int hizSampler = glGenSamplers();

    private Shader hierarchicalTraversal = Shader.make(this.printf)
            .add(ShaderType.COMPUTE, "voxy:lod/hierarchical/traversal.comp")
            .compile();

    public HierarchicalOcclusionRenderer() {

    }

    private void bind() {

    }

    public void render(int depthBuffer, int width, int height) {
        this.nodeManager.upload();

        //Make hiz
        this.hiz.buildMipChain(depthBuffer, width, height);
        glMemoryBarrier(GL_PIXEL_BUFFER_BARRIER_BIT);
        this.hierarchicalTraversal.bind();

        {
            //Bind stuff here

            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, this.nodeManager.nodeBuffer.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, this.nodeManager.requestQueue.id);

            //Bind the hiz buffer
            glBindSampler(0, this.hizSampler);
            glBindTextureUnit(0, this.hiz.getHizTextureId());
        }
        this.printf.bind();
        {
            //Dispatch hierarchies
        }

        this.nodeManager.download();
        this.printf.download();
    }

    public void free() {
        this.printf.free();
        this.hiz.free();
        this.nodeManager.free();
        glDeleteSamplers(this.hizSampler);
    }
}
