package me.cortex.voxy.client.core.gl;

import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gpu.*;

/**
 * OpenGL implementation of the RenderBackend interface.
 * Delegates to the existing GL wrapper classes (GlBuffer, GlTexture, etc.)
 * and the GLCompat compatibility layer.
 */
public class GlRenderBackend implements RenderBackend {

    @Override
    public BackendType getType() {
        return BackendType.OPENGL;
    }

    // --- Resource Creation ---

    @Override
    public IGpuBuffer createBuffer(long size) {
        return new GlBuffer(size);
    }

    @Override
    public IGpuBuffer createBuffer(long size, int flags) {
        return new GlBuffer(size, flags);
    }

    @Override
    public IGpuBuffer createBuffer(long size, int flags, boolean zero) {
        return new GlBuffer(size, flags, zero);
    }

    @Override
    public IGpuTexture createTexture() {
        return new GlTexture();
    }

    @Override
    public IGpuTexture createTexture(int type) {
        return new GlTexture(type);
    }

    @Override
    public IGpuFramebuffer createFramebuffer() {
        return new GlFramebuffer();
    }

    @Override
    public IGpuRenderBuffer createRenderBuffer(int format, int width, int height) {
        return new GlRenderBuffer(format, width, height);
    }

    @Override
    public IGpuVertexArray createVertexArray() {
        return new GlVertexArray();
    }

    @Override
    public IGpuFence createFence() {
        return new GlFence();
    }

    @Override
    public IGpuPersistentBuffer createPersistentBuffer(long size, int flags) {
        return new GlPersistentMappedBuffer(size, flags);
    }

    // --- Texture Operations (delegate to GLCompat) ---

    @Override
    public void bindTextureUnit(int unit, int texture) {
        GLCompat.bindTextureUnit(unit, texture);
    }

    @Override
    public void bindTextureUnit(int unit, int target, int texture) {
        GLCompat.bindTextureUnit(unit, target, texture);
    }

    @Override
    public void textureParameteri(int texture, int pname, int param) {
        GLCompat.textureParameteri(texture, pname, param);
    }

    @Override
    public void textureParameteri(int texture, int target, int pname, int param) {
        GLCompat.textureParameteri(texture, target, pname, param);
    }

    @Override
    public void textureParameterf(int texture, int pname, float param) {
        GLCompat.textureParameterf(texture, pname, param);
    }

    @Override
    public void textureParameterf(int texture, int target, int pname, float param) {
        GLCompat.textureParameterf(texture, target, pname, param);
    }

    @Override
    public void textureSubImage2D(int texture, int target, int level, int x, int y, int w, int h, int format, int type, long addr) {
        GLCompat.textureSubImage2D(texture, target, level, x, y, w, h, format, type, addr);
    }

    @Override
    public void textureStorage2D(int texture, int target, int levels, int format, int width, int height) {
        GLCompat.textureStorage2D(texture, target, levels, format, width, height);
    }

    // --- Framebuffer Operations ---

    @Override
    public void framebufferTexture(int fbo, int attachment, int texture, int level, int target) {
        GLCompat.framebufferTexture(fbo, attachment, texture, level, target);
    }

    @Override
    public void framebufferRenderbuffer(int fbo, int attachment, int renderbuffer) {
        GLCompat.framebufferRenderbuffer(fbo, attachment, renderbuffer);
    }

    @Override
    public void framebufferDrawBuffers(int fbo, int... buffers) {
        GLCompat.framebufferDrawBuffers(fbo, buffers);
    }

    @Override
    public int checkFramebufferStatus(int fbo) {
        return GLCompat.checkFramebufferStatus(fbo);
    }

    @Override
    public void clearDepthFramebuffer(int fbo, float depth) {
        GLCompat.clearDepthFramebuffer(fbo, depth);
    }

    @Override
    public void clearDepthStencilFramebuffer(int fbo, float depth, int stencil) {
        GLCompat.clearDepthStencilFramebuffer(fbo, depth, stencil);
    }

    @Override
    public void blitFramebuffer(int readFbo, int drawFbo, int srcX0, int srcY0, int srcX1, int srcY1,
                                int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        GLCompat.blitFramebuffer(readFbo, drawFbo, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    // --- Renderbuffer Operations ---

    @Override
    public int createRenderbufferId() {
        return GLCompat.createRenderbuffer();
    }

    @Override
    public void renderbufferStorage(int renderbuffer, int format, int width, int height) {
        GLCompat.renderbufferStorage(renderbuffer, format, width, height);
    }

    // --- Debug ---

    @Override
    public void objectLabel(int type, int id, String name) {
        if (GlDebug.GL_DEBUG) {
            org.lwjgl.opengl.GL43C.glObjectLabel(type, id, name);
        }
    }

    // --- Capabilities ---

    @Override
    public boolean hasCompute() {
        return Capabilities.INSTANCE.compute;
    }

    @Override
    public boolean hasIndirectCount() {
        return Capabilities.INSTANCE.indirectCount;
    }

    @Override
    public boolean hasIndirectParameters() {
        return Capabilities.INSTANCE.indirectParameters;
    }

    @Override
    public boolean hasSparseBuffer() {
        return Capabilities.INSTANCE.sparseBuffer;
    }

    @Override
    public long getMaxSSBOSize() {
        return Capabilities.INSTANCE.ssboMaxSize;
    }

    @Override
    public int getStaticVAO() {
        return GlVertexArray.STATIC_VAO;
    }

    @Override
    public int getBufferCount() {
        return GlBuffer.getCount();
    }

    @Override
    public long getBufferTotalSize() {
        return GlBuffer.getTotalSize();
    }

    @Override
    public int getTextureCount() {
        return GlTexture.getCount();
    }

    @Override
    public long getTextureEstimatedTotalSize() {
        return GlTexture.getEstimatedTotalSize();
    }

    @Override
    public void memoryBarrier(int flags) {
        org.lwjgl.opengl.GL42.glMemoryBarrier(flags);
    }

    @Override
    public void copyBufferSubData(IGpuBuffer src, IGpuBuffer dst, long srcOffset, long dstOffset, long size) {
        copyBufferSubDataById(src.id(), dst.id(), srcOffset, dstOffset, size);
    }

    @Override
    public void copyBufferSubData(IGpuPersistentBuffer src, IGpuBuffer dst, long srcOffset, long dstOffset, long size) {
        copyBufferSubDataById(src.id(), dst.id(), srcOffset, dstOffset, size);
    }

    @Override
    public void copyBufferSubData(IGpuBuffer src, IGpuPersistentBuffer dst, long srcOffset, long dstOffset, long size) {
        copyBufferSubDataById(src.id(), dst.id(), srcOffset, dstOffset, size);
    }

    private static final int GL_COPY_READ_BUFFER_BINDING = 0x8F36;
    private static final int GL_COPY_WRITE_BUFFER_BINDING = 0x8F37;

    private static void copyBufferSubDataById(int srcId, int dstId, long srcOffset, long dstOffset, long size) {
        if (size <= 0) return;
        boolean hasDSA = org.lwjgl.opengl.GL.getCapabilities().GL_ARB_direct_state_access
                || org.lwjgl.opengl.GL.getCapabilities().OpenGL45;
        if (hasDSA) {
            org.lwjgl.opengl.GL45C.glCopyNamedBufferSubData(srcId, dstId, srcOffset, dstOffset, size);
        } else {
            int prevRead = org.lwjgl.opengl.GL15C.glGetInteger(GL_COPY_READ_BUFFER_BINDING);
            int prevWrite = org.lwjgl.opengl.GL15C.glGetInteger(GL_COPY_WRITE_BUFFER_BINDING);
            org.lwjgl.opengl.GL15C.glBindBuffer(org.lwjgl.opengl.GL31C.GL_COPY_READ_BUFFER, srcId);
            org.lwjgl.opengl.GL15C.glBindBuffer(org.lwjgl.opengl.GL31C.GL_COPY_WRITE_BUFFER, dstId);
            org.lwjgl.opengl.GL31C.glCopyBufferSubData(
                    org.lwjgl.opengl.GL31C.GL_COPY_READ_BUFFER,
                    org.lwjgl.opengl.GL31C.GL_COPY_WRITE_BUFFER,
                    srcOffset, dstOffset, size);
            org.lwjgl.opengl.GL15C.glBindBuffer(org.lwjgl.opengl.GL31C.GL_COPY_READ_BUFFER, prevRead);
            org.lwjgl.opengl.GL15C.glBindBuffer(org.lwjgl.opengl.GL31C.GL_COPY_WRITE_BUFFER, prevWrite);
        }
    }

    // --- Render pass encoding ---

    @Override
    public RenderEncoder beginRenderPass(RenderPassDesc desc) {
        // Allocate a transient framebuffer for the pass and attach the color/depth
        // textures. We don't cache because Voxy's M2 caller (just clears) is
        // low-frequency; M5+ will introduce a per-attachment-set FBO cache when
        // pipeline state binding lands.
        int fbo = org.lwjgl.opengl.GL45C.glCreateFramebuffers();
        int[] drawBuffers = new int[Math.max(1, desc.colorAttachments().size())];
        for (int i = 0; i < desc.colorAttachments().size(); i++) {
            RenderPassDesc.ColorAttachment c = desc.colorAttachments().get(i);
            int attachment = org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0 + i;
            org.lwjgl.opengl.GL45C.glNamedFramebufferTexture(fbo, attachment, c.texture().id(), c.level());
            drawBuffers[i] = attachment;
            if (c.loadAction() == RenderPassDesc.LoadAction.CLEAR) {
                org.lwjgl.opengl.GL45C.glClearNamedFramebufferfv(fbo, org.lwjgl.opengl.GL30C.GL_COLOR, i,
                        new float[]{c.clearR(), c.clearG(), c.clearB(), c.clearA()});
            }
        }
        if (!desc.colorAttachments().isEmpty()) {
            org.lwjgl.opengl.GL45C.glNamedFramebufferDrawBuffers(fbo, drawBuffers);
        }
        if (desc.depthAttachment() != null) {
            RenderPassDesc.DepthAttachment d = desc.depthAttachment();
            org.lwjgl.opengl.GL45C.glNamedFramebufferTexture(fbo,
                    org.lwjgl.opengl.GL30C.GL_DEPTH_ATTACHMENT, d.texture().id(), d.level());
            if (d.loadAction() == RenderPassDesc.LoadAction.CLEAR) {
                org.lwjgl.opengl.GL45C.glClearNamedFramebufferfv(fbo,
                        org.lwjgl.opengl.GL30C.GL_DEPTH, 0, new float[]{d.clearDepth()});
            }
        }
        org.lwjgl.opengl.GL45C.glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER, fbo);
        org.lwjgl.opengl.GL11C.glViewport(0, 0, desc.viewportWidth(), desc.viewportHeight());

        return new GlRenderEncoder(fbo);
    }

    @Override
    public void submit() {
        org.lwjgl.opengl.GL11C.glFlush();
    }

    @Override
    public IGpuPipeline createGraphicsPipeline(GraphicsPipelineDesc desc) {
        return new GlGraphicsPipeline(desc);
    }

    @Override
    public IGpuPipeline createComputePipeline(ComputePipelineDesc desc) {
        return new GlComputePipeline(desc);
    }

    @Override
    public ComputeEncoder beginComputePass() {
        return new GlComputeEncoder();
    }

    @Override
    public IGpuSampler createSampler(SamplerDesc desc) {
        return new GlSampler(desc);
    }

    @Override
    public IGpuIndirectCommandBuffer createIndirectCommandBuffer(int maxCommands) {
        return new GlIndirectCommandBuffer(maxCommands);
    }

    /**
     * Concrete encoder for the GL backend. Each pass owns a transient VAO so
     * vertex attribute formats from {@link GlGraphicsPipeline#vertexLayout}
     * can be applied without polluting Voxy's shared VAOs. Push-constant
     * emulation uses a per-encoder UBO; resource bindings are forwarded to
     * the relevant {@code glBind*} entry points.
     */
    private static final class GlRenderEncoder implements RenderEncoder {
        private final int fbo;
        /** Per-encoder VAO created on first vertex-attribute set; 0 means uninitialized. */
        private int vao;
        /** Currently bound graphics pipeline (drives index-type and vertex layout). */
        private GlGraphicsPipeline pipeline;
        /** Index type captured from {@link #bindIndexBuffer} (GL_UNSIGNED_SHORT / GL_UNSIGNED_INT). */
        private int indexGlType = org.lwjgl.opengl.GL11C.GL_UNSIGNED_INT;
        /** Index buffer offset captured from {@link #bindIndexBuffer}, in bytes. */
        private long indexBaseOffset;
        /** Lazy UBO used to back {@link #setBytes}; created on first use. */
        private int pushUbo;
        private long pushUboCapacity;
        private boolean closed;

        GlRenderEncoder(int fbo) { this.fbo = fbo; }

        @Override
        public void setPipeline(IGpuPipeline pipeline) {
            if (!(pipeline instanceof GlGraphicsPipeline gl)) {
                throw new IllegalArgumentException(
                        "GlRenderEncoder.setPipeline requires GlGraphicsPipeline, got "
                                + (pipeline == null ? "null" : pipeline.getClass().getName()));
            }
            this.pipeline = gl;
            org.lwjgl.opengl.GL20C.glUseProgram(gl.program());
            GlPipelineStateApplier.apply(gl.state);
            applyVertexLayout(gl.vertexLayout);
        }

        @Override
        public void setBuffer(int binding, IGpuBuffer buffer, long offset) {
            if (offset == 0) {
                org.lwjgl.opengl.GL43C.glBindBufferBase(
                        org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER, binding, buffer.id());
            } else {
                org.lwjgl.opengl.GL43C.glBindBufferRange(
                        org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER, binding,
                        buffer.id(), offset, buffer.size() - offset);
            }
        }

        @Override
        public void setTexture(int binding, IGpuTexture texture) {
            // Render-pass setTexture is "sampled texture" semantics: bind the
            // texture object to texture unit `binding`. setSampler(binding, ...)
            // pairs with this via glBindSampler at the same unit.
            org.lwjgl.opengl.GL45C.glBindTextureUnit(binding, texture.id());
        }

        @Override
        public void setSampler(int binding, IGpuSampler sampler) {
            if (!(sampler instanceof GlSampler gl)) {
                throw new IllegalArgumentException(
                        "GlRenderEncoder.setSampler requires GlSampler, got "
                                + (sampler == null ? "null" : sampler.getClass().getName()));
            }
            org.lwjgl.opengl.GL33C.glBindSampler(binding, gl.handle());
        }

        @Override
        public void setBytes(int binding, long dataAddr, int dataSize) {
            ensurePushUbo(dataSize);
            org.lwjgl.opengl.GL45C.nglNamedBufferSubData(this.pushUbo, 0, dataSize, dataAddr);
            org.lwjgl.opengl.GL30C.glBindBufferRange(
                    org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER, binding,
                    this.pushUbo, 0, dataSize);
        }

        @Override
        public void bindVertexBuffer(int slot, IGpuBuffer buffer, long offset) {
            ensureVao();
            int stride = strideForSlot(slot);
            org.lwjgl.opengl.GL45C.glVertexArrayVertexBuffer(this.vao, slot,
                    buffer.id(), offset, stride);
        }

        @Override
        public void bindIndexBuffer(IGpuBuffer buffer, int indexType, long offset) {
            ensureVao();
            this.indexGlType = (indexType == INDEX_TYPE_UINT16)
                    ? org.lwjgl.opengl.GL11C.GL_UNSIGNED_SHORT
                    : org.lwjgl.opengl.GL11C.GL_UNSIGNED_INT;
            this.indexBaseOffset = offset;
            org.lwjgl.opengl.GL45C.glVertexArrayElementBuffer(this.vao, buffer.id());
        }

        @Override
        public void setViewport(float x, float y, float width, float height,
                                 float minDepth, float maxDepth) {
            org.lwjgl.opengl.GL41C.glViewportIndexedf(0, x, y, width, height);
            org.lwjgl.opengl.GL41C.glDepthRangeIndexed(0, minDepth, maxDepth);
        }

        @Override
        public void setScissor(int x, int y, int width, int height) {
            org.lwjgl.opengl.GL30C.glEnable(org.lwjgl.opengl.GL11C.GL_SCISSOR_TEST);
            org.lwjgl.opengl.GL41C.glScissorIndexed(0, x, y, width, height);
        }

        @Override
        public void draw(int primitiveType, int firstVertex, int vertexCount,
                         int instanceCount, int baseInstance) {
            ensureVao();
            int mode = mapPrimitive(primitiveType);
            if (baseInstance == 0) {
                org.lwjgl.opengl.GL31C.glDrawArraysInstanced(mode, firstVertex, vertexCount, instanceCount);
            } else {
                org.lwjgl.opengl.GL42C.glDrawArraysInstancedBaseInstance(
                        mode, firstVertex, vertexCount, instanceCount, baseInstance);
            }
        }

        @Override
        public void drawIndexed(int primitiveType, int indexCount, int instanceCount,
                                 int firstIndex, int vertexOffset, int firstInstance) {
            ensureVao();
            int mode = mapPrimitive(primitiveType);
            int indexSize = (this.indexGlType == org.lwjgl.opengl.GL11C.GL_UNSIGNED_SHORT) ? 2 : 4;
            long indexByteOffset = this.indexBaseOffset + (long) firstIndex * indexSize;
            if (firstInstance == 0 && vertexOffset == 0) {
                org.lwjgl.opengl.GL31C.glDrawElementsInstanced(
                        mode, indexCount, this.indexGlType, indexByteOffset, instanceCount);
            } else {
                org.lwjgl.opengl.GL42C.glDrawElementsInstancedBaseVertexBaseInstance(
                        mode, indexCount, this.indexGlType, indexByteOffset,
                        instanceCount, vertexOffset, firstInstance);
            }
        }

        @Override
        public void drawIndirect(int primitiveType, IGpuBuffer buffer, long offset,
                                  int drawCount, int stride) {
            ensureVao();
            int mode = mapPrimitive(primitiveType);
            int prev = org.lwjgl.opengl.GL15C.glGetInteger(
                    org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER_BINDING);
            org.lwjgl.opengl.GL15C.glBindBuffer(
                    org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER, buffer.id());
            org.lwjgl.opengl.GL43C.glMultiDrawArraysIndirect(mode, offset, drawCount, stride);
            org.lwjgl.opengl.GL15C.glBindBuffer(
                    org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER, prev);
        }

        @Override
        public void drawIndexedIndirect(int primitiveType, IGpuBuffer buffer, long offset,
                                         int drawCount, int stride) {
            ensureVao();
            int mode = mapPrimitive(primitiveType);
            int prev = org.lwjgl.opengl.GL15C.glGetInteger(
                    org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER_BINDING);
            org.lwjgl.opengl.GL15C.glBindBuffer(
                    org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER, buffer.id());
            org.lwjgl.opengl.GL43C.glMultiDrawElementsIndirect(
                    mode, this.indexGlType, offset, drawCount, stride);
            org.lwjgl.opengl.GL15C.glBindBuffer(
                    org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER, prev);
        }

        @Override
        public void executeCommandsInBuffer(me.cortex.voxy.client.core.gpu.IGpuIndirectCommandBuffer icb,
                                             IGpuBuffer rangeBuffer, long rangeOffset) {
            // GL has no ICB resource — the encoder operates directly on buffer
            // bindings. The migration plan keeps this method as a no-op on GL
            // for the cross-backend interface symmetry; MDIC's GL path keeps
            // calling drawIndexedIndirectCount with its own draw+count buffers.
            // If MDIC migrates fully to the ICB model, this becomes the entry
            // point and we'd lower it to glMultiDrawElementsIndirectCountARB
            // using rangeBuffer as the count source (uint32 at rangeOffset+0
            // for length; location is implicit in the existing draw offset).
            throw new UnsupportedOperationException(
                    "GlRenderEncoder.executeCommandsInBuffer: GL path uses drawIndexedIndirectCount directly. "
                            + "ICB execution is Metal-specific until MDIC migrates to the ICB model.");
        }

        @Override
        public void drawIndexedIndirectCount(int primitiveType,
                                              IGpuBuffer drawBuffer, long drawOffset,
                                              IGpuBuffer countBuffer, long countOffset,
                                              int maxDrawCount, int stride) {
            ensureVao();
            int mode = mapPrimitive(primitiveType);
            int prevDraw = org.lwjgl.opengl.GL15C.glGetInteger(
                    org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER_BINDING);
            int prevParam = org.lwjgl.opengl.GL15C.glGetInteger(
                    org.lwjgl.opengl.ARBIndirectParameters.GL_PARAMETER_BUFFER_BINDING_ARB);
            org.lwjgl.opengl.GL15C.glBindBuffer(
                    org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER, drawBuffer.id());
            org.lwjgl.opengl.GL15C.glBindBuffer(
                    org.lwjgl.opengl.ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB, countBuffer.id());
            org.lwjgl.opengl.ARBIndirectParameters.glMultiDrawElementsIndirectCountARB(
                    mode, this.indexGlType, drawOffset, countOffset, maxDrawCount, stride);
            org.lwjgl.opengl.GL15C.glBindBuffer(
                    org.lwjgl.opengl.ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB, prevParam);
            org.lwjgl.opengl.GL15C.glBindBuffer(
                    org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER, prevDraw);
        }

        @Override
        public void close() {
            if (this.closed) return;
            this.closed = true;
            if (this.vao != 0) {
                org.lwjgl.opengl.GL45C.glDeleteVertexArrays(this.vao);
                this.vao = 0;
            }
            if (this.pushUbo != 0) {
                org.lwjgl.opengl.GL15C.glDeleteBuffers(this.pushUbo);
                this.pushUbo = 0;
            }
            org.lwjgl.opengl.GL45C.glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER, 0);
            org.lwjgl.opengl.GL45C.glDeleteFramebuffers(this.fbo);
        }

        private void ensureVao() {
            if (this.vao == 0) {
                this.vao = org.lwjgl.opengl.GL45C.glCreateVertexArrays();
                org.lwjgl.opengl.GL30C.glBindVertexArray(this.vao);
            } else {
                int active = org.lwjgl.opengl.GL15C.glGetInteger(
                        org.lwjgl.opengl.GL30C.GL_VERTEX_ARRAY_BINDING);
                if (active != this.vao) {
                    org.lwjgl.opengl.GL30C.glBindVertexArray(this.vao);
                }
            }
        }

        private void applyVertexLayout(me.cortex.voxy.client.core.gpu.VertexLayout layout) {
            if (layout == null || layout.attributes.length == 0) return;
            ensureVao();
            for (var attr : layout.attributes) {
                int loc = attr.location;
                org.lwjgl.opengl.GL45C.glEnableVertexArrayAttrib(this.vao, loc);
                int glType = GlVertexFormatMap.glType(attr.format);
                int comps = GlVertexFormatMap.components(attr.format);
                if (GlVertexFormatMap.integerAttribute(attr.format)) {
                    org.lwjgl.opengl.GL45C.glVertexArrayAttribIFormat(
                            this.vao, loc, comps, glType, attr.offset);
                } else {
                    org.lwjgl.opengl.GL45C.glVertexArrayAttribFormat(
                            this.vao, loc, comps, glType,
                            GlVertexFormatMap.normalized(attr.format), attr.offset);
                }
                org.lwjgl.opengl.GL45C.glVertexArrayAttribBinding(this.vao, loc, attr.bufferSlot);
            }
            for (var buf : layout.buffers) {
                if (buf.stepRate == me.cortex.voxy.client.core.gpu.VertexLayout.StepRate.PER_INSTANCE) {
                    org.lwjgl.opengl.GL45C.glVertexArrayBindingDivisor(this.vao, buf.slot, 1);
                } else {
                    org.lwjgl.opengl.GL45C.glVertexArrayBindingDivisor(this.vao, buf.slot, 0);
                }
            }
        }

        private int strideForSlot(int slot) {
            if (this.pipeline == null) return 0;
            for (var buf : this.pipeline.vertexLayout.buffers) {
                if (buf.slot == slot) return buf.stride;
            }
            return 0;
        }

        private static int mapPrimitive(int primitiveType) {
            return switch (primitiveType) {
                case PRIMITIVE_TRIANGLES -> org.lwjgl.opengl.GL11C.GL_TRIANGLES;
                case PRIMITIVE_TRIANGLE_STRIP -> org.lwjgl.opengl.GL11C.GL_TRIANGLE_STRIP;
                case PRIMITIVE_LINES -> org.lwjgl.opengl.GL11C.GL_LINES;
                case PRIMITIVE_POINTS -> org.lwjgl.opengl.GL11C.GL_POINTS;
                default -> throw new IllegalArgumentException("Unknown primitive: " + primitiveType);
            };
        }

        private void ensurePushUbo(int size) {
            long needed = (size + 255) & ~255L;
            if (this.pushUbo == 0) {
                this.pushUbo = org.lwjgl.opengl.GL45C.glCreateBuffers();
                this.pushUboCapacity = Math.max(needed, 256);
                org.lwjgl.opengl.GL45C.glNamedBufferData(this.pushUbo, this.pushUboCapacity,
                        org.lwjgl.opengl.GL15C.GL_DYNAMIC_DRAW);
            } else if (needed > this.pushUboCapacity) {
                this.pushUboCapacity = needed;
                org.lwjgl.opengl.GL45C.glNamedBufferData(this.pushUbo, this.pushUboCapacity,
                        org.lwjgl.opengl.GL15C.GL_DYNAMIC_DRAW);
            }
        }
    }
}
