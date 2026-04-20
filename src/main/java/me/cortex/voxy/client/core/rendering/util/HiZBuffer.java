package me.cortex.voxy.client.core.rendering.util;

import me.cortex.voxy.client.core.gl.GLCompat;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.gpu.IGpuFramebuffer;
import me.cortex.voxy.client.core.gpu.IGpuTexture;
import me.cortex.voxy.client.core.gpu.RenderBackendFactory;
import org.lwjgl.opengl.GL11;

import static org.lwjgl.opengl.ARBShaderImageLoadStore.GL_TEXTURE_FETCH_BARRIER_BIT;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL33.glBindSampler;
import static org.lwjgl.opengl.GL33.glGenSamplers;
import static org.lwjgl.opengl.GL33C.glDeleteSamplers;
import static org.lwjgl.opengl.GL33C.glSamplerParameteri;
import static org.lwjgl.opengl.GL42C.GL_FRAMEBUFFER_BARRIER_BIT;
import static org.lwjgl.opengl.GL42C.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static me.cortex.voxy.client.core.gl.GLCompat.textureParameteri;
import static me.cortex.voxy.client.core.gl.GLCompat.bindTextureUnit;

public class HiZBuffer {
    private final Shader hiz = Shader.make()
            .add(ShaderType.VERTEX, "voxy:hiz/blit.vsh")
            .add(ShaderType.FRAGMENT, "voxy:hiz/blit.fsh")
            .compile()
            .name("HiZ Builder");
    private final IGpuFramebuffer fb = RenderBackendFactory.get().createFramebuffer().name("HiZ");
    private final int sampler = glGenSamplers();
    private final int type;
    private IGpuTexture texture;
    private int levels;
    private int width;
    private int height;

    public HiZBuffer() {
        this(GL_DEPTH24_STENCIL8);
    }
    public HiZBuffer(int type) {
        GLCompat.framebufferDrawBuffers(this.fb.id(), GL_NONE);
        this.type = type;
    }

    private void alloc(int width, int height) {
        this.levels = (int)Math.ceil(Math.log(Math.max(width, height))/Math.log(2));
        //We dont care about e.g. 1x1 size texture since you dont get meshlets that big to cover such a large area
        //this.levels -= 1;//Arbitrary size, shinks the max level by alot and saves a significant amount of processing time
        // (could probably increase it to be defined by a max meshlet coverage computation thing)

        //GL_DEPTH_COMPONENT32F //Cant use this as it does not match the depth format of the provided depth buffer
        this.texture = RenderBackendFactory.get().createTexture().store(this.type, this.levels, width, height).name("HiZ");
        textureParameteri(this.texture.id(), GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
        textureParameteri(this.texture.id(), GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        textureParameteri(this.texture.id(), GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_NONE);
        textureParameteri(this.texture.id(), GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        textureParameteri(this.texture.id(), GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

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
        if (this.width != Integer.highestOneBit(width) || this.height != Integer.highestOneBit(height)) {
            if (this.texture != null) {
                this.texture.free();
                this.texture = null;
            }
            this.alloc(Integer.highestOneBit(width), Integer.highestOneBit(height));
        }
        glBindVertexArray(RenderBackendFactory.get().getStaticVAO());
        int boundFB = GL11.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        this.hiz.bind();
        glBindFramebuffer(GL_FRAMEBUFFER, this.fb.id());

        glDepthFunc(GL_ALWAYS);
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);


        bindTextureUnit(0, GL_TEXTURE_2D, srcDepthTex);
        glBindSampler(0, this.sampler);
        glUniform1i(0, 0);
        int cw = this.width;
        int ch = this.height;
        for (int i = 0; i < this.levels; i++) {
            this.fb.bind(GL_DEPTH_ATTACHMENT, this.texture, i);
            glViewport(0, 0, cw, ch); cw = Math.max(cw/2, 1); ch = Math.max(ch/2, 1);
            glDrawArrays(GL_TRIANGLE_FAN, 0, 4);
            glMemoryBarrier(GL_FRAMEBUFFER_BARRIER_BIT|GL_TEXTURE_FETCH_BARRIER_BIT|GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
            textureParameteri(this.texture.id(), GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, i);
            textureParameteri(this.texture.id(), GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, i);
            if (i==0) {
                bindTextureUnit(0, GL_TEXTURE_2D, this.texture.id());
            }
        }
        textureParameteri(this.texture.id(), GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);
        textureParameteri(this.texture.id(), GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 1000);//TODO: CHECK IF ITS -1 or -0

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
        return this.texture.id();
    }

    public int getPackedLevels() {
        return (this.width<<16)|this.height;//+1
    }
}
