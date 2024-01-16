package me.cortex.voxelmon.client.core.rendering;

import me.cortex.voxelmon.client.core.gl.GlFramebuffer;
import me.cortex.voxelmon.client.core.gl.GlTexture;
import me.cortex.voxelmon.client.core.gl.shader.Shader;
import me.cortex.voxelmon.client.core.gl.shader.ShaderType;
import org.lwjgl.opengl.GL11C;

import static org.lwjgl.opengl.ARBFramebufferObject.*;
import static org.lwjgl.opengl.ARBShaderImageLoadStore.glBindImageTexture;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15C.GL_READ_WRITE;
import static org.lwjgl.opengl.GL44C.glBindImageTextures;
import static org.lwjgl.opengl.GL45C.glTextureBarrier;

public class PostProcessing {
    private final GlFramebuffer framebuffer;
    private int width;
    private int height;
    private GlTexture colour;
    private GlTexture depthStencil;

    private final Shader ssao = Shader.make()
            .add(ShaderType.COMPUTE, "voxelmon:lod/ssao/ssao.comp")
            .compile();

    //private final Shader blit = Shader.make()
    //        .add(ShaderType.VERTEX, "voxelmon:lod/blit_nodepth/quad.vert")
    //        .add(ShaderType.FRAGMENT, "voxelmon:lod/blit_nodepth/quad.frag")
    //        .compile();

    public PostProcessing() {
        this.framebuffer = new GlFramebuffer();
    }

    public void setSize(int width, int height) {
        if (this.width != width || this.height != height) {
            this.width = width;
            this.height = height;
            if (this.colour != null) {
                this.colour.free();
                this.depthStencil.free();
            }

            this.colour = new GlTexture().store(GL_RGBA8, 1, width, height);
            this.depthStencil = new GlTexture().store(GL_DEPTH24_STENCIL8, 1, width, height);

            this.framebuffer.bind(GL_COLOR_ATTACHMENT0, this.colour);
            this.framebuffer.bind(GL_DEPTH_STENCIL_ATTACHMENT, this.depthStencil);
            this.framebuffer.verify();
        }
    }

    //Bind and clears the post processing frame buffer
    public void bindClearFramebuffer() {
        glBindFramebuffer(GL_FRAMEBUFFER, this.framebuffer.id);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
    }

    //Executes the post processing and emits to whatever framebuffer is currently bound via a blit
    public void renderPost(int outputFb) {
        this.ssao.bind();
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, this.depthStencil.id);
        glBindImageTexture(0, this.colour.id, 0, false,0, GL_READ_WRITE, GL_RGBA8);
        //glDispatchCompute(this.width/32, this.height/32, 1);
        glTextureBarrier();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, this.colour.id);
        glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
    }

    public void shutdown() {
        this.framebuffer.free();
        if (this.colour != null) this.colour.free();
        if (this.depthStencil != null) this.depthStencil.free();
        this.ssao.free();
        //this.blit.free();
    }
}
