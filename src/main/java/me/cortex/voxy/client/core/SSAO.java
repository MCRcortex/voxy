package me.cortex.voxy.client.core;

import me.cortex.voxy.client.core.gl.Capabilities;
import me.cortex.voxy.client.core.gl.GlTexture;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.rendering.Viewport;
import org.joml.Matrix4f;
import org.joml.Random;
import org.lwjgl.system.MemoryStack;

import java.util.List;

import static org.lwjgl.opengl.ARBComputeShader.glDispatchCompute;
import static org.lwjgl.opengl.ARBDirectStateAccess.glTextureParameteri;
import static org.lwjgl.opengl.ARBShaderImageLoadStore.glBindImageTexture;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL14C.GL_TEXTURE_COMPARE_MODE;
import static org.lwjgl.opengl.GL15C.GL_READ_WRITE;
import static org.lwjgl.opengl.GL20C.nglUniformMatrix4fv;
import static org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.opengl.GL45.glGetNamedFramebufferAttachmentParameteri;
import static org.lwjgl.opengl.GL45C.glBindTextureUnit;
import static org.lwjgl.opengl.GL45C.glCreateSamplers;

public class SSAO {
    public static enum SSAOMode {
        AUTO,
        BASIC,
        BETTER,
        BEST
    }

    public static SSAO createSSAO(RenderProperties properties, SSAOMode mode) {
        if (mode == SSAOMode.BASIC) {
            return new SSAO(properties);
        } else if (mode == SSAOMode.BETTER) {
            return new SSAO(properties, true, 12);
        } else if (mode == SSAOMode.BEST) {
            return new SSAO(properties, true, 24);
        } else if (mode == SSAOMode.AUTO) {
            if (Capabilities.INSTANCE.canQueryGpuMemory) {
                if (Capabilities.INSTANCE.totalDedicatedMemory < 2_500_000_000L) {
                    return createSSAO(properties, SSAOMode.BASIC);//Create a basic instance (cant query memory (probably intel igpu or less then 2.5gb vram)
                } else if (Capabilities.INSTANCE.totalDedicatedMemory < 7_000_000_000L) {
                    return createSSAO(properties, SSAOMode.BETTER);//Less then 7gb of dedicated vram create a better instance (mid range dgpus (they can probably do best just fine but just in case)
                } else {
                    return createSSAO(properties, SSAOMode.BEST);//create the best ssao
                }
            } else {
                if (Capabilities.INSTANCE.isAmd) {
                    return createSSAO(properties, SSAOMode.BETTER);
                } else {
                    return createSSAO(properties, SSAOMode.BASIC);
                }
            }
        } else {
            throw new IllegalArgumentException();
        }
    }

    private final Shader ssaoCompute;
    private final boolean isBetterSSAO;
    private final int spp;

    private final int depthSampler;
    public SSAO(RenderProperties properties) {
        this(properties, false, 0);
    }

    public SSAO(RenderProperties properties, boolean betterSSAO, int samples) {
        var builder = Shader.make()
                .apply(properties::apply)
                .add(ShaderType.COMPUTE, "voxy:post/ssao.comp");

        this.spp = samples;

        boolean useConstArray = true;

        this.isBetterSSAO = betterSSAO;
        if (betterSSAO) {
            builder.define("BETTER_SSAO")
                    .defineIf("SSAO_STEPS", samples!=0, samples)
                    .defineIf("USE_GENERATED_SAMPLE_POINTS", useConstArray);

            if (useConstArray) {
                String array = "";
                for (int i = 0; i < samples; i++) {
                    array += "vec2(";
                    float a = (((float) i) + 0.5f) * (1.0f / samples);

                    float base = (float) (i * (1.0 / 1.6180339887) + 0.5);
                    float r = (float) Math.sqrt(base % 1);
                    float theta = a * 6.2831853f;

                    array += (float) (r * Math.cos(theta));
                    array += "f, ";
                    array += (float) (r * Math.sin(theta));
                    array += "f)";
                    if (i != samples - 1) {
                        array += ", ";
                    }
                }
                builder.replace("%%CONST_ARRAY%%", array);
            }
        }

        this.ssaoCompute = builder.compile();

        this.depthSampler = glCreateSamplers();
        //UHHHH IS THIS EVEN VALID FOR A DEPTH SAMPLER????
        if (this.isBetterSSAO) {
            glSamplerParameteri(this.depthSampler, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
            glSamplerParameteri(this.depthSampler, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        } else {
            glSamplerParameteri(this.depthSampler, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glSamplerParameteri(this.depthSampler, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        }
        glSamplerParameteri(this.depthSampler, GL_TEXTURE_COMPARE_MODE, GL_NONE);
        glSamplerParameteri(this.depthSampler, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glSamplerParameteri(this.depthSampler, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    }

    public void computeSSAO(Viewport<?> viewport, GlTexture colourOut, GlTexture colourIn, GlTexture baseDepthTex, int sourceFramebuffer) {
        this.ssaoCompute.bind();
        //The matrices
        try (var stack = MemoryStack.stackPush()) {
            long ptr = stack.nmalloc(4*4*4);
            var scratch = new Matrix4f();
            if (this.isBetterSSAO) {
                viewport.projection.getToAddress(ptr);
                nglUniformMatrix4fv(4, 1, false, ptr);//Proj
                viewport.projection.invert(scratch).getToAddress(ptr);
                nglUniformMatrix4fv(5, 1, false, ptr);//invProj
                viewport.modelView.getToAddress(ptr);
                nglUniformMatrix4fv(6, 1, false, ptr);//MV (the normal matrix)
                viewport.vanillaProjection.invert(scratch).getToAddress(ptr);
                nglUniformMatrix4fv(7, 1, false, ptr);//sourceInvProj
            } else {
                viewport.MVP.getToAddress(ptr);
                nglUniformMatrix4fv(3, 1, false, ptr);//MVP
                viewport.MVP.invert(scratch).getToAddress(ptr);
                nglUniformMatrix4fv(4, 1, false, ptr);//invMVP
            }
        }

        glBindImageTexture(0, colourOut.id, 0, false,0, GL_READ_WRITE, GL_RGBA8);
        glBindTextureUnit(1, colourIn.id);
        glBindSampler(1,0);
        glBindTextureUnit(2, baseDepthTex.id);
        glBindSampler(2, this.depthSampler);

        if (this.isBetterSSAO) {
            int depthTexture = glGetNamedFramebufferAttachmentParameteri(sourceFramebuffer, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            glBindTextureUnit(3, depthTexture);
            glBindSampler(3, this.depthSampler);
        }

        glDispatchCompute((viewport.width+7)/8, (viewport.height+7)/8, 1);

        glBindTextureUnit(1, 0);
        glBindSampler(1,0);
        glBindTextureUnit(2, 0);
        glBindSampler(2, 0);
        glBindTextureUnit(3, 0);
        glBindSampler(3, 0);
    }

    public void free() {
        glDeleteSamplers(this.depthSampler);
        this.ssaoCompute.free();
    }

    public void addDebugInfo(List<String> debugLines) {
        debugLines.add("SSAO: "+(this.isBetterSSAO?("new ("+this.spp+" spp)"):"basic"));
    }
}
