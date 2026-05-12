package me.cortex.voxy.client.core.gl;

import me.cortex.voxy.client.core.gpu.ComputeEncoder;
import me.cortex.voxy.client.core.gpu.IGpuBuffer;
import me.cortex.voxy.client.core.gpu.IGpuPipeline;
import me.cortex.voxy.client.core.gpu.IGpuSampler;
import me.cortex.voxy.client.core.gpu.IGpuTexture;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL45C;

import static org.lwjgl.opengl.GL20C.glUseProgram;

/**
 * OpenGL implementation of {@link ComputeEncoder}.
 *
 * Compute on GL is just driver-state — there is no encoder object. We allocate
 * a small per-encoder UBO for {@link #setBytes} (push-constant emulation) and
 * release it on {@link #close}; everything else lowers directly to global GL
 * state.
 */
public final class GlComputeEncoder implements ComputeEncoder {

    private boolean closed;
    /** Last bound pipeline — kept so future hooks (validation, label) can use it. */
    private GlComputePipeline pipeline;
    /** Lazy UBO used to back {@link #setBytes}; created on first use. */
    private int pushUbo;
    private long pushUboCapacity;

    GlComputeEncoder() {
    }

    @Override
    public void setPipeline(IGpuPipeline pipeline) {
        if (!(pipeline instanceof GlComputePipeline gl)) {
            throw new IllegalArgumentException(
                    "GlComputeEncoder.setPipeline requires GlComputePipeline, got "
                            + (pipeline == null ? "null" : pipeline.getClass().getName()));
        }
        this.pipeline = gl;
        glUseProgram(gl.program());
    }

    @Override
    public void setBuffer(int binding, IGpuBuffer buffer, long offset) {
        if (offset == 0) {
            GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, binding, buffer.id());
        } else {
            GL43C.glBindBufferRange(GL43C.GL_SHADER_STORAGE_BUFFER, binding,
                    buffer.id(), offset, buffer.size() - offset);
        }
    }

    @Override
    public void setBuffer(int binding, me.cortex.voxy.client.core.gpu.IGpuPersistentBuffer buffer, long offset, long size) {
        GL43C.glBindBufferRange(GL43C.GL_SHADER_STORAGE_BUFFER, binding,
                buffer.id(), offset, size);
    }

    @Override
    public void setTexture(int binding, IGpuTexture texture) {
        // Sampled-texture binding: lives on texture unit `binding`, paired
        // with the sampler from setSampler(binding, ...). Use setStorageImage
        // for image2D / image3D writers.
        org.lwjgl.opengl.GL45C.glBindTextureUnit(binding, texture == null ? 0 : texture.id());
    }

    @Override
    public void setStorageImage(int binding, IGpuTexture texture, int level) {
        int format = texture.getFormat();
        boolean layered = texture.getType() == GL30C.GL_TEXTURE_2D_ARRAY
                || texture.getType() == GL30C.GL_TEXTURE_3D
                || texture.getType() == GL30C.GL_TEXTURE_CUBE_MAP;
        GL42C.glBindImageTexture(binding, texture.id(), level, layered, 0,
                GL15C.GL_READ_WRITE, format);
    }

    @Override
    public void setSampler(int binding, IGpuSampler sampler) {
        if (!(sampler instanceof GlSampler gl)) {
            throw new IllegalArgumentException(
                    "GlComputeEncoder.setSampler requires GlSampler, got "
                            + (sampler == null ? "null" : sampler.getClass().getName()));
        }
        // The companion sampled texture is bound by setTexture-as-sampled at the
        // same binding in render passes; for compute, callers pair setSampler
        // with a separate texture-unit bind via the existing GLCompat helpers.
        org.lwjgl.opengl.GL33C.glBindSampler(binding, gl.handle());
    }

    @Override
    public void setBytes(int binding, long dataAddr, int dataSize) {
        ensurePushUbo(dataSize);
        // Upload the inline data and bind to UBO target. Voxy GLSL uses
        // `uniform Block { ... }` for these so UBO is the right target.
        GL45C.nglNamedBufferSubData(this.pushUbo, 0, dataSize, dataAddr);
        GL30C.glBindBufferRange(GL31C.GL_UNIFORM_BUFFER, binding, this.pushUbo, 0, dataSize);
    }

    @Override
    public void dispatch(int groupCountX, int groupCountY, int groupCountZ) {
        GL43C.glDispatchCompute(groupCountX, groupCountY, groupCountZ);
    }

    @Override
    public void dispatchIndirect(IGpuBuffer buffer, long offset) {
        int prev = GL15C.glGetInteger(GL43C.GL_DISPATCH_INDIRECT_BUFFER_BINDING);
        GL15C.glBindBuffer(GL43C.GL_DISPATCH_INDIRECT_BUFFER, buffer.id());
        GL43C.glDispatchComputeIndirect(offset);
        GL15C.glBindBuffer(GL43C.GL_DISPATCH_INDIRECT_BUFFER, prev);
    }

    @Override
    public void barrier(int srcStages, int dstStages) {
        // Translate ComputeEncoder.BARRIER_* flags into a glMemoryBarrier mask.
        // GL collapses src/dst into one mask; we OR both sides and emit once.
        int mask = 0;
        int combined = srcStages | dstStages;
        if ((combined & BARRIER_SHADER) != 0) {
            mask |= GL43C.GL_SHADER_STORAGE_BARRIER_BIT
                    | GL42C.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT
                    | GL42C.GL_TEXTURE_FETCH_BARRIER_BIT
                    | GL42C.GL_UNIFORM_BARRIER_BIT;
        }
        if ((combined & BARRIER_INDIRECT) != 0) {
            mask |= GL42C.GL_COMMAND_BARRIER_BIT;
        }
        if ((combined & BARRIER_TRANSFER) != 0) {
            mask |= GL42C.GL_BUFFER_UPDATE_BARRIER_BIT
                    | GL42C.GL_PIXEL_BUFFER_BARRIER_BIT;
        }
        if (mask != 0) {
            GL42C.glMemoryBarrier(mask);
        }
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        if (this.pushUbo != 0) {
            GL15C.glDeleteBuffers(this.pushUbo);
            this.pushUbo = 0;
        }
    }

    private void ensurePushUbo(int size) {
        // Round up to 256-byte alignment to satisfy UBO offset alignment on
        // most drivers and reduce reallocations when a caller's sizes vary.
        long needed = (size + 255) & ~255L;
        if (this.pushUbo == 0) {
            this.pushUbo = GL45C.glCreateBuffers();
            this.pushUboCapacity = Math.max(needed, 256);
            GL45C.glNamedBufferData(this.pushUbo, this.pushUboCapacity, GL15C.GL_DYNAMIC_DRAW);
        } else if (needed > this.pushUboCapacity) {
            this.pushUboCapacity = needed;
            GL45C.glNamedBufferData(this.pushUbo, this.pushUboCapacity, GL15C.GL_DYNAMIC_DRAW);
        }
    }
}
