package me.cortex.voxelmon.core.rendering;

import me.cortex.voxelmon.core.gl.GlFramebuffer;
import me.cortex.voxelmon.core.gl.GlTexture;
import me.cortex.voxelmon.core.gl.shader.Shader;
import me.cortex.voxelmon.core.gl.shader.ShaderType;

import static org.lwjgl.opengl.ARBFramebufferObject.*;
import static org.lwjgl.opengl.ARBShaderImageLoadStore.glBindImageTexture;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15C.GL_READ_ONLY;
import static org.lwjgl.opengl.GL15C.GL_READ_WRITE;
import static org.lwjgl.opengl.GL30C.GL_R32F;
import static org.lwjgl.opengl.GL42C.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static org.lwjgl.opengl.GL43C.glDispatchCompute;
import static org.lwjgl.opengl.GL44C.glBindImageTextures;
import static org.lwjgl.opengl.GL45C.glBlitNamedFramebuffer;
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
        glBindImageTexture(0, this.colour.id, 0, false,0, GL_READ_WRITE, GL_RGBA8);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, this.depthStencil.id);
        //glDispatchCompute(this.width/32, this.height/32, 1);
        glTextureBarrier();
        glBlitNamedFramebuffer(this.framebuffer.id, outputFb, 0,0, this.width, this.height, 0, 0, this.width, this.height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
    }

    public void shutdown() {
        this.framebuffer.free();
        if (this.colour != null) this.colour.free();
        if (this.depthStencil != null) this.depthStencil.free();
        this.ssao.free();
    }
}
