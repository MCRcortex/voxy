package me.cortex.voxy.client.core.rendering.util;

import me.cortex.voxy.client.core.gl.GlFramebuffer;
import me.cortex.voxy.client.core.gl.GlTexture;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.rendering.RenderService;
import org.lwjgl.opengl.GL11;

import static org.lwjgl.opengl.ARBDirectStateAccess.*;
import static org.lwjgl.opengl.ARBShaderImageLoadStore.GL_TEXTURE_FETCH_BARRIER_BIT;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL33.glBindSampler;
import static org.lwjgl.opengl.GL33.glGenSamplers;
import static org.lwjgl.opengl.GL33C.glDeleteSamplers;
import static org.lwjgl.opengl.GL33C.glSamplerParameteri;
import static org.lwjgl.opengl.GL42C.GL_FRAMEBUFFER_BARRIER_BIT;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static org.lwjgl.opengl.GL43C.glCopyImageSubData;
import static org.lwjgl.opengl.GL45C.glTextureBarrier;

public class HiZBuffer {
    private final Shader hiz = Shader.make()
            .add(ShaderType.VERTEX, "voxy:hiz/blit.vsh")
            .add(ShaderType.FRAGMENT, "voxy:hiz/blit.fsh")
            .compile()
            .name("HiZ Builder");
    private final GlFramebuffer fb = new GlFramebuffer().name("HiZ");
    private final int sampler = glGenSamplers();
    private final int type;
    private GlTexture texture;
    private int levels;
    private int width;
    private int height;

    public HiZBuffer() {
        this(GL_DEPTH24_STENCIL8);
    }
    public HiZBuffer(int type) {
        glNamedFramebufferDrawBuffer(this.fb.id, GL_NONE);
        this.type = type;
    }

    private void alloc(int width, int height) {
        this.levels = (int)Math.ceil(Math.log(Math.max(width, height))/Math.log(2));
        //We dont care about e.g. 1x1 size texture since you dont get meshlets that big to cover such a large area
        //this.levels -= 1;//Arbitrary size, shinks the max level by alot and saves a significant amount of processing time
        // (could probably increase it to be defined by a max meshlet coverage computation thing)

        //GL_DEPTH_COMPONENT32F //Cant use this as it does not match the depth format of the provided depth buffer
        this.texture = new GlTexture().store(this.type, this.levels, width, height).name("HiZ");
        glTextureParameteri(this.texture.id, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
        glTextureParameteri(this.texture.id, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTextureParameteri(this.texture.id, GL_TEXTURE_COMPARE_MODE, GL_NONE);
        glTextureParameteri(this.texture.id, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTextureParameteri(this.texture.id, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glSamplerParameteri(this.sampler, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
        glSamplerParameteri(this.sampler, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glSamplerParameteri(this.sampler, GL_TEXTURE_COMPARE_MODE, GL_NONE);
        glSamplerParameteri(this.sampler, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glSamplerParameteri(this.sampler, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        this.width  = width;
        this.height = height;

        this.fb.bind(GL_DEPTH_ATTACHMENT, this.texture, 0).verify();
    }

    public void buildMipChain(int srcDepthTex, int width, int height) {
        if (this.width != width || this.height != height) {
            if (this.texture != null) {
                this.texture.free();
                this.texture = null;
            }
            this.alloc(width, height);
        }
        glBindVertexArray(RenderService.STATIC_VAO);
        int boundFB = GL11.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        this.hiz.bind();
        glBindFramebuffer(GL_FRAMEBUFFER, this.fb.id);

        glDepthFunc(GL_ALWAYS);
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);


        //System.err.println("SRC: " + GlTexture.getRawTextureType(srcDepthTex) + " DST: " + this.texture.id);
        glCopyImageSubData(srcDepthTex, GL_TEXTURE_2D, 0,0,0,0,
                this.texture.id, GL_TEXTURE_2D, 0,0,0,0,
                width, height, 1);


        glBindTextureUnit(0, this.texture.id);
        glBindSampler(0, this.sampler);
        glUniform1i(0, 0);
        int cw = this.width;
        int ch = this.height;
        glViewport(0, 0, cw, ch);
        for (int i = 0; i < this.levels-1; i++) {
            glTextureParameteri(this.texture.id, GL_TEXTURE_BASE_LEVEL, i);
            glTextureParameteri(this.texture.id, GL_TEXTURE_MAX_LEVEL, i);
            this.fb.bind(GL_DEPTH_ATTACHMENT, this.texture, i+1);
            cw = Math.max(cw/2, 1); ch = Math.max(ch/2, 1); glViewport(0, 0, cw, ch);
            glDrawArrays(GL_TRIANGLE_FAN, 0, 4);
            glTextureBarrier();
            glMemoryBarrier(GL_FRAMEBUFFER_BARRIER_BIT|GL_TEXTURE_FETCH_BARRIER_BIT);
        }
        glTextureParameteri(this.texture.id, GL_TEXTURE_BASE_LEVEL, 0);
        glTextureParameteri(this.texture.id, GL_TEXTURE_MAX_LEVEL, 1000);//TODO: CHECK IF ITS -1 or -0

        glDepthFunc(GL_LEQUAL);
        glDisable(GL_DEPTH_TEST);
        glBindFramebuffer(GL_FRAMEBUFFER, boundFB);
        glViewport(0, 0, width, height);
        glBindVertexArray(0);
    }

    public void free() {
        this.fb.free();
        if (this.texture != null) {
            this.texture.free();
            this.texture = null;
        }
        glDeleteSamplers(this.sampler);
        this.hiz.free();
    }

    public int getHizTextureId() {
        return this.texture.id;
    }
}
