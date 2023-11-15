package me.cortex.voxelmon.core.rendering;

import me.cortex.voxelmon.core.gl.GlFramebuffer;
import me.cortex.voxelmon.core.gl.GlTexture;

import static org.lwjgl.opengl.ARBFramebufferObject.*;
import static org.lwjgl.opengl.GL11.GL_RGBA8;

public class PostProcessing {
    private final GlFramebuffer framebuffer;
    private int width;
    private int height;
    private GlTexture colour;
    private GlTexture depthStencil;

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

    }

    //Executes the post processing and emits to whatever framebuffer is currently bound via a blit
    public void renderPost() {

    }

}
