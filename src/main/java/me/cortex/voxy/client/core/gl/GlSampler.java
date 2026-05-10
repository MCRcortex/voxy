package me.cortex.voxy.client.core.gl;

import me.cortex.voxy.client.core.gpu.IGpuSampler;
import me.cortex.voxy.client.core.gpu.PipelineState;
import me.cortex.voxy.client.core.gpu.SamplerDesc;
import org.lwjgl.opengl.GL11C;

import static org.lwjgl.opengl.GL11C.GL_LINEAR;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_WRAP_R;
import static org.lwjgl.opengl.GL13C.GL_CLAMP_TO_BORDER;
import static org.lwjgl.opengl.GL14C.GL_MIRRORED_REPEAT;
import static org.lwjgl.opengl.GL11C.GL_LINEAR_MIPMAP_LINEAR;
import static org.lwjgl.opengl.GL11C.GL_LINEAR_MIPMAP_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_NEAREST_MIPMAP_LINEAR;
import static org.lwjgl.opengl.GL11C.GL_NEAREST_MIPMAP_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_REPEAT;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL14C.GL_TEXTURE_LOD_BIAS;
import static org.lwjgl.opengl.GL14C.GL_TEXTURE_COMPARE_FUNC;
import static org.lwjgl.opengl.GL14C.GL_TEXTURE_COMPARE_MODE;
import static org.lwjgl.opengl.GL30C.GL_COMPARE_REF_TO_TEXTURE;
import static org.lwjgl.opengl.GL30C.GL_NONE;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_MAX_LOD;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_MIN_LOD;
import static org.lwjgl.opengl.GL33C.glDeleteSamplers;
import static org.lwjgl.opengl.GL33C.glGenSamplers;
import static org.lwjgl.opengl.GL33C.glSamplerParameterf;
import static org.lwjgl.opengl.GL33C.glSamplerParameteri;

/** OpenGL implementation of {@link IGpuSampler}. */
public final class GlSampler implements IGpuSampler {

    private int handle;

    public GlSampler(SamplerDesc desc) {
        this.handle = glGenSamplers();

        // Combine min filter + mip filter into the GL enum (GL doesn't separate them).
        int glMinFilter = mapMinFilter(desc.minFilter, desc.mipFilter);
        int glMagFilter = mapMagFilter(desc.magFilter);
        glSamplerParameteri(this.handle, GL_TEXTURE_MIN_FILTER, glMinFilter);
        glSamplerParameteri(this.handle, GL_TEXTURE_MAG_FILTER, glMagFilter);

        glSamplerParameteri(this.handle, GL_TEXTURE_WRAP_S, mapWrap(desc.wrapS));
        glSamplerParameteri(this.handle, GL_TEXTURE_WRAP_T, mapWrap(desc.wrapT));
        glSamplerParameteri(this.handle, GL_TEXTURE_WRAP_R, mapWrap(desc.wrapR));

        glSamplerParameterf(this.handle, GL_TEXTURE_MIN_LOD, desc.lodMinClamp);
        // GL caps max lod at a reasonable upper bound; SamplerDesc allows Float.MAX_VALUE
        // which would fail the GL float-as-fixed encoding. Clamp to 1000.
        float lodMax = Math.min(desc.lodMaxClamp, 1000f);
        glSamplerParameterf(this.handle, GL_TEXTURE_MAX_LOD, lodMax);

        if (desc.compareEnable) {
            glSamplerParameteri(this.handle, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
            glSamplerParameteri(this.handle, GL_TEXTURE_COMPARE_FUNC, mapCompare(desc.compareOp));
        } else {
            glSamplerParameteri(this.handle, GL_TEXTURE_COMPARE_MODE, GL_NONE);
        }

        if (desc.label != null && GlDebug.GL_DEBUG) {
            org.lwjgl.opengl.GL43C.glObjectLabel(org.lwjgl.opengl.GL43C.GL_SAMPLER, this.handle, desc.label);
        }
    }

    public int handle() {
        return this.handle;
    }

    @Override
    public void close() {
        if (this.handle != 0) {
            glDeleteSamplers(this.handle);
            this.handle = 0;
        }
    }

    private static int mapMinFilter(SamplerDesc.Filter min, SamplerDesc.MipFilter mip) {
        if (mip == SamplerDesc.MipFilter.NOT_MIPMAPPED) {
            return min == SamplerDesc.Filter.NEAREST ? GL_NEAREST : GL_LINEAR;
        }
        if (min == SamplerDesc.Filter.NEAREST) {
            return mip == SamplerDesc.MipFilter.NEAREST ? GL_NEAREST_MIPMAP_NEAREST : GL_NEAREST_MIPMAP_LINEAR;
        }
        return mip == SamplerDesc.MipFilter.NEAREST ? GL_LINEAR_MIPMAP_NEAREST : GL_LINEAR_MIPMAP_LINEAR;
    }

    private static int mapMagFilter(SamplerDesc.Filter f) {
        return f == SamplerDesc.Filter.NEAREST ? GL_NEAREST : GL_LINEAR;
    }

    private static int mapWrap(SamplerDesc.Wrap w) {
        return switch (w) {
            case CLAMP_TO_EDGE -> GL_CLAMP_TO_EDGE;
            case REPEAT -> GL_REPEAT;
            case MIRRORED_REPEAT -> GL_MIRRORED_REPEAT;
            case CLAMP_TO_ZERO -> GL_CLAMP_TO_BORDER;  // closest GL equivalent
        };
    }

    private static int mapCompare(PipelineState.CompareOp op) {
        return switch (op) {
            case NEVER -> GL11C.GL_NEVER;
            case LESS -> GL11C.GL_LESS;
            case EQUAL -> GL11C.GL_EQUAL;
            case LESS_EQUAL -> GL11C.GL_LEQUAL;
            case GREATER -> GL11C.GL_GREATER;
            case NOT_EQUAL -> GL11C.GL_NOTEQUAL;
            case GREATER_EQUAL -> GL11C.GL_GEQUAL;
            case ALWAYS -> GL11C.GL_ALWAYS;
        };
    }
}
