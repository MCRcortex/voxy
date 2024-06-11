package me.cortex.voxy.client.core.rendering;

import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.rendering.hierarchical.NodeManager;
import me.cortex.voxy.common.util.HierarchicalBitSet;

import static org.lwjgl.opengl.GL42C.*;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL43C.glDispatchCompute;
import static org.lwjgl.opengl.GL44.GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT;

public class HierarchicalOcclusionRenderer {
    private final int workgroup_dispatch_size_x;//The number of workgroups required to saturate the gpu efficiently
    private final NodeManager nodeManager = new NodeManager(null);
    private final HiZBuffer hiz = new HiZBuffer();


    private Shader hiercarchialShader = Shader.make()
            .add(ShaderType.COMPUTE, "voxy:lod/hierarchical/selector.comp")
            .compile();

    public HierarchicalOcclusionRenderer(int workgroup_size) {
        this.workgroup_dispatch_size_x = workgroup_size;

    }

    private void bind() {

    }

    public void render(int depthBuffer, int width, int height) {
        //Make hiz
        this.hiz.buildMipChain(depthBuffer, width, height);
        //Node upload phase
        this.nodeManager.uploadPhase();
        //Node download phase (pulls from previous frame (should maybe result in lower latency)) also clears and resets the queues
        this.nodeManager.downloadPhase();
        //Bind all the resources
        this.bind();
        //run hierachial selection shader
        this.hiercarchialShader.bind();
        //barrier
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT|GL_UNIFORM_BARRIER_BIT|GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT|GL_FRAMEBUFFER_BARRIER_BIT);
        //Emit enough work to fully populate the gpu
        glDispatchCompute(this.workgroup_dispatch_size_x, 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT|GL_COMMAND_BARRIER_BIT|GL_UNIFORM_BARRIER_BIT);
    }
}
