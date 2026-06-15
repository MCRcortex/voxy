package me.cortex.neovoxy.client.core.rendering.util;

import me.cortex.neovoxy.client.core.gl.GlFramebuffer;
import me.cortex.neovoxy.client.core.gl.GlTexture;
import me.cortex.neovoxy.client.core.gl.GlVertexArray;
import me.cortex.neovoxy.client.core.gl.shader.Shader;
import me.cortex.neovoxy.client.core.gl.shader.ShaderType;
import org.lwjgl.opengl.GL11;

import static org.lwjgl.opengl.ARBDirectStateAccess.*;
import static org.lwjgl.opengl.ARBShaderImageLoadStore.GL_TEXTURE_FETCH_BARRIER_BIT;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL33.glBindSampler;
import static org.lwjgl.opengl.GL33.glGenSamplers;
import static org.lwjgl.opengl.GL33C.glDeleteSamplers;
import static org.lwjgl.opengl.GL33C.glSamplerParameteri;
import static org.lwjgl.opengl.GL42C.*;
import static org.lwjgl.opengl.GL43C.glDispatchCompute;
import static org.lwjgl.opengl.GL45C.glTextureBarrier;

public class HiZBuffer2 {
    private final Shader hizMip = Shader.make()
            .add(ShaderType.COMPUTE, "neovoxy:hiz/hiz.comp")
            .compile();
    private final Shader hizInitial = Shader.make()
            .add(ShaderType.VERTEX, "neovoxy:hiz/blit.vsh")
            .add(ShaderType.FRAGMENT, "neovoxy:hiz/blit.fsh")
            .define("OUTPUT_COLOUR")
            .compile();
    private final GlFramebuffer fb = new GlFramebuffer().name("HiZ");
    private final int sampler = glGenSamplers();
    private final int type;
    private GlTexture texture;
    private int levels;
    private int width;
    private int height;

    public HiZBuffer2() {
        this(GL_R32F);
    }
    public HiZBuffer2(int type) {
        glNamedFramebufferDrawBuffer(this.fb.id, GL_COLOR_ATTACHMENT0);
        this.type = type;
    }

    private void alloc(int width, int height) {
        this.levels = Math.min(7,(int)Math.ceil(Math.log(Math.max(width, height))/Math.log(2)));
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

        this.fb.bind(GL_COLOR_ATTACHMENT0, this.texture, 0).verify();
    }

    public void buildMipChain(int srcDepthTex, int width, int height) {
        if (this.width != Integer.highestOneBit(width) || this.height != Integer.highestOneBit(height)) {
            if (this.texture != null) {
                this.texture.free();
                this.texture = null;
            }
            this.alloc(Integer.highestOneBit(width), Integer.highestOneBit(height));
        }


        {//Mip down to initial chain
            int boundFB = GL11.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);

            glBindVertexArray(GlVertexArray.STATIC_VAO);
            this.hizInitial.bind();
            glBindFramebuffer(GL_FRAMEBUFFER, this.fb.id);

            glDisable(GL_DEPTH_TEST);


            glBindTextureUnit(0, srcDepthTex);
            glBindSampler(0, this.sampler);
            glUniform1i(0, 0);

            glViewport(0, 0, this.width, this.height);

            glDrawArrays(GL_TRIANGLE_FAN, 0, 4);

            glTextureBarrier();
            glMemoryBarrier(GL_FRAMEBUFFER_BARRIER_BIT|GL_TEXTURE_FETCH_BARRIER_BIT);

            glBindFramebuffer(GL_FRAMEBUFFER, boundFB);
            glViewport(0, 0, width, height);
            glBindVertexArray(0);
        }

        {//Compute based Mipping
            this.hizMip.bind();

            glUniform2f(0, 1f/this.width, 1f/this.height);
            glBindTextureUnit(0, this.texture.id);
            glBindSampler(0, this.sampler);
            for (int i = 1; i < 7; i++) {
                glBindImageTexture(i, this.texture.id, i, false, 0, GL_WRITE_ONLY, GL_R32F);
            }

            glDispatchCompute(this.width/64, this.height/64, 1);

            glBindSampler(0, 0);
            for (int i =0;i<7;i++)
                glBindTextureUnit(i, 0);

        }


    }

    public void free() {
        this.fb.free();
        if (this.texture != null) {
            this.texture.free();
            this.texture = null;
        }
        glDeleteSamplers(this.sampler);
        this.hizInitial.free();
        this.hizMip.free();
    }

    public int getHizTextureId() {
        return this.texture.id;
    }

    public int getPackedLevels() {
        return ((Integer.numberOfTrailingZeros(this.width))<<16)|(Integer.numberOfTrailingZeros(this.height));//+1
    }
}
