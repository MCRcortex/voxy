package me.cortex.voxy.client.core.model.bakery;

import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.GlFramebuffer;
import me.cortex.voxy.client.core.gl.GlTexture;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;

import static org.lwjgl.opengl.ARBDirectStateAccess.glClearNamedFramebufferfv;
import static org.lwjgl.opengl.ARBDirectStateAccess.glTextureParameteri;
import static org.lwjgl.opengl.ARBShaderImageLoadStore.GL_FRAMEBUFFER_BARRIER_BIT;
import static org.lwjgl.opengl.ARBShaderImageLoadStore.GL_PIXEL_BUFFER_BARRIER_BIT;
import static org.lwjgl.opengl.ARBShaderImageLoadStore.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;
import static org.lwjgl.opengl.ARBShaderImageLoadStore.GL_TEXTURE_UPDATE_BARRIER_BIT;
import static org.lwjgl.opengl.ARBShaderImageLoadStore.glMemoryBarrier;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.GL_STENCIL_INDEX;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.opengl.GL45.glClearNamedFramebufferfi;

public class GlViewCapture {
    private final int width;
    private final int height;
    private final GlTexture colourTex;
    private final GlTexture depthTex;
    private final GlTexture stencilTex;
    final GlFramebuffer framebuffer;
    private final Shader copyOutShader;

    public GlViewCapture(int width, int height) {
        this.width = width;
        this.height = height;
        this.colourTex = new GlTexture().store(GL_RGBA8, 1, width*3, height*2).name("ModelBakeryColour");
        this.depthTex = new GlTexture().store(GL_DEPTH24_STENCIL8, 1, width*3, height*2).name("ModelBakeryDepth");
        //TODO: FIXME: Mesa is broken when trying to read from a sampler of GL_STENCIL_INDEX
        // it seems to just ignore the value set in GL_DEPTH_STENCIL_TEXTURE_MODE
        glTextureParameteri(this.depthTex.id, GL_DEPTH_STENCIL_TEXTURE_MODE, GL_STENCIL_INDEX);
        this.stencilTex = this.depthTex.createView();
        glTextureParameteri(this.depthTex.id, GL_DEPTH_STENCIL_TEXTURE_MODE, GL_DEPTH_COMPONENT);

        this.framebuffer = new GlFramebuffer().bind(GL_COLOR_ATTACHMENT0, this.colourTex).bind(GL_DEPTH_STENCIL_ATTACHMENT, this.depthTex).verify().name("ModelFramebuffer");

        glTextureParameteri(this.stencilTex.id, GL_DEPTH_STENCIL_TEXTURE_MODE, GL_STENCIL_INDEX);

        this.copyOutShader = Shader.makeAuto()
                .define("WIDTH", width)
                .define("HEIGHT", height)
                .define("COLOUR_IN_BINDING", 0)
                .define("DEPTH_IN_BINDING", 1)
                .define("STENCIL_IN_BINDING", 2)
                .define("BUFFER_OUT_BINDING", 3)
                .add(ShaderType.COMPUTE, "voxy:bakery/bufferreorder.comp")
                .compile()
                .name("ModelBakeryOut")
                .texture("COLOUR_IN_BINDING", 0, this.colourTex)
                .texture("DEPTH_IN_BINDING", 0, this.depthTex)
                .texture("STENCIL_IN_BINDING", 0, this.stencilTex);
    }

    public void emitToStream(int buffer, int offset) {
        this.copyOutShader.bind();
        glBindBufferRange(GL_SHADER_STORAGE_BUFFER, 3, buffer, offset, (this.width*3L)*(this.height*2L)*4L*2);//its 2*4 because colour + depth stencil
        glMemoryBarrier(GL_FRAMEBUFFER_BARRIER_BIT|GL_TEXTURE_UPDATE_BARRIER_BIT|GL_PIXEL_BUFFER_BARRIER_BIT|GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);//Am not sure if barriers are right
        glDispatchCompute(3, 2, 1);
    }

    public void clear() {
        glClearNamedFramebufferfv(this.framebuffer.id, GL_COLOR, 0, new float[]{0,0,0,0});
        glClearNamedFramebufferfi(this.framebuffer.id, GL_DEPTH_STENCIL, 0, 1.0f, 0);
    }

    public void free() {
        this.framebuffer.free();
        this.colourTex.free();
        this.stencilTex.free();
        this.depthTex.free();
        this.copyOutShader.free();
    }
}
