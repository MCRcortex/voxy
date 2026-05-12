package me.cortex.voxy.client.core.metal;

import me.cortex.voxy.client.core.gpu.*;
import me.cortex.voxy.common.Logger;

import java.util.concurrent.atomic.AtomicLong;

import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;

/**
 * Metal rendering backend for macOS Apple Silicon (M-series) support.
 *
 * This backend translates Voxy's rendering operations to Metal API calls
 * via the MetalNative JNI bridge. It manages the Metal device, command queue,
 * and shared event for fence synchronization.
 *
 * Metal architectural differences from OpenGL:
 *   - No global state machine; state is captured in pipeline state objects
 *   - No framebuffer objects; render targets are per-pass via render pass descriptors
 *   - No VAOs; vertex layout is part of the render pipeline state
 *   - Buffers in shared storage are always CPU-visible (unified memory)
 *   - Texture state (filtering, wrapping) is set via sampler objects, not on textures
 *   - Shaders are compiled to MTLLibrary from MSL (not GLSL)
 */
public class MetalRenderBackend implements RenderBackend {

    private final long device;

    /** Native MTLDevice handle. Exposed for ICB/IOSurface callers that bypass the backend's resource creators. */
    public long device() {
        return this.device;
    }
    private final long commandQueue;
    private final long sharedEvent;
    private final AtomicLong fenceCounter = new AtomicLong(1);
    private final long maxBufferLength;
    /** Lazily allocated MTLCommandBuffer for the current frame; 0 when no commands are queued. */
    private long activeCommandBuffer = 0;

    public MetalRenderBackend() {
        if (!MetalNative.load()) {
            throw new RuntimeException("Metal native library is not available");
        }

        this.device = MetalNative.mtlCreateSystemDefaultDevice();
        if (this.device == 0) {
            throw new RuntimeException("Failed to create Metal device");
        }

        String deviceName = MetalNative.mtlDeviceGetName(this.device);
        Logger.info("Metal device: " + deviceName);

        this.commandQueue = MetalNative.mtlDeviceNewCommandQueue(this.device);
        if (this.commandQueue == 0) {
            throw new RuntimeException("Failed to create Metal command queue");
        }

        this.sharedEvent = MetalNative.mtlDeviceNewSharedEvent(this.device);
        if (this.sharedEvent == 0) {
            throw new RuntimeException("Failed to create Metal shared event");
        }

        this.maxBufferLength = MetalNative.mtlDeviceMaxBufferLength(this.device);
    }

    @Override
    public BackendType getType() {
        return BackendType.METAL;
    }

    // --- Resource Creation ---

    @Override
    public IGpuBuffer createBuffer(long size) {
        return createBuffer(size, 0, true);
    }

    @Override
    public IGpuBuffer createBuffer(long size, int flags) {
        return createBuffer(size, flags, true);
    }

    @Override
    public IGpuBuffer createBuffer(long size, int flags, boolean zero) {
        return new MetalBuffer(this.device, size,
                MetalNative.MTLResourceStorageModeShared, zero);
    }

    @Override
    public IGpuTexture createTexture() {
        return new MetalTexture(this.device, GL_TEXTURE_2D);
    }

    @Override
    public IGpuTexture createTexture(int type) {
        return new MetalTexture(this.device, type);
    }

    @Override
    public IGpuFramebuffer createFramebuffer() {
        return new MetalFramebuffer();
    }

    @Override
    public IGpuRenderBuffer createRenderBuffer(int format, int width, int height) {
        return new MetalRenderBuffer(this.device, format, width, height);
    }

    @Override
    public IGpuVertexArray createVertexArray() {
        return new MetalVertexDescriptor();
    }

    @Override
    public IGpuFence createFence() {
        long targetValue = this.fenceCounter.getAndIncrement();

        long cmdBuffer = MetalNative.mtlCommandQueueNewCommandBuffer(this.commandQueue);
        MetalNative.mtlCommandBufferEncodeSignalEvent(cmdBuffer, this.sharedEvent, targetValue);
        MetalNative.mtlCommandBufferCommit(cmdBuffer);
        MetalNative.mtlRelease(cmdBuffer);

        return new MetalFence(this.sharedEvent, targetValue);
    }

    @Override
    public IGpuPersistentBuffer createPersistentBuffer(long size, int flags) {
        return new MetalPersistentBuffer(this.device, size, flags);
    }

    // --- Texture Operations ---

    @Override
    public void bindTextureUnit(int unit, int texture) {
        // Metal binds textures per-encoder, not globally. Tracked for draw-time binding.
    }

    @Override
    public void bindTextureUnit(int unit, int target, int texture) {
        // Same — no global texture unit state in Metal
    }

    @Override
    public void textureParameteri(int texture, int pname, int param) {
        // Metal sets filtering/wrapping via MTLSamplerState, not on the texture
    }

    @Override
    public void textureParameteri(int texture, int target, int pname, int param) {
        // Same
    }

    @Override
    public void textureParameterf(int texture, int pname, float param) {
        // Sampler state
    }

    @Override
    public void textureParameterf(int texture, int target, int pname, float param) {
        // Same
    }

    @Override
    public void textureSubImage2D(int texture, int target, int level, int x, int y,
                                   int w, int h, int format, int type, long addr) {
        long texHandle = MetalHandleMap.getHandle(texture);
        int bpp = (int) MetalFormatUtil.bytesPerPixel(format);
        MetalNative.mtlTextureReplaceRegion(texHandle, level, x, y, w, h, addr, w * bpp);
    }

    @Override
    public void textureStorage2D(int texture, int target, int levels, int format, int width, int height) {
        // Metal allocates storage at texture creation time (MetalTexture.store())
    }

    // --- Framebuffer Operations ---

    @Override
    public void framebufferTexture(int fbo, int attachment, int texture, int level, int target) {
        // Handled by MetalFramebuffer.bind() when using the object API
    }

    @Override
    public void framebufferRenderbuffer(int fbo, int attachment, int renderbuffer) {
        // Handled by MetalFramebuffer.bind()
    }

    @Override
    public void framebufferDrawBuffers(int fbo, int... buffers) {
        // Metal configures this via render pass descriptor color attachments
    }

    @Override
    public int checkFramebufferStatus(int fbo) {
        return 0x8CD5; // GL_FRAMEBUFFER_COMPLETE — Metal validates at encoder creation
    }

    @Override
    public void clearDepthFramebuffer(int fbo, float depth) {
        // Deferred to render pass descriptor load action
    }

    @Override
    public void clearDepthStencilFramebuffer(int fbo, float depth, int stencil) {
        // Deferred to render pass descriptor load action
    }

    @Override
    public void blitFramebuffer(int readFbo, int drawFbo, int srcX0, int srcY0, int srcX1, int srcY1,
                                 int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        // TODO: Implement via blit command encoder or fullscreen render pass
    }

    // --- Renderbuffer Operations ---

    @Override
    public int createRenderbufferId() {
        return MetalHandleMap.register(1); // Placeholder — actual texture created in createRenderBuffer
    }

    @Override
    public void renderbufferStorage(int renderbuffer, int format, int width, int height) {
        // Metal renderbuffers are textures — storage is allocated at creation time
    }

    // --- Debug ---

    @Override
    public void objectLabel(int type, int id, String name) {
        try {
            long handle = MetalHandleMap.getHandle(id);
            MetalNative.mtlSetLabel(handle, name);
        } catch (IllegalArgumentException e) {
            // Unknown ID — silently ignore for debug labeling
        }
    }

    // --- Capabilities ---

    @Override
    public boolean hasCompute() {
        return true;
    }

    @Override
    public boolean hasIndirectCount() {
        return true;
    }

    @Override
    public boolean hasIndirectParameters() {
        return true;
    }

    @Override
    public boolean hasSparseBuffer() {
        return false;
    }

    @Override
    public long getMaxSSBOSize() {
        return this.maxBufferLength;
    }

    @Override
    public int getStaticVAO() {
        return 0; // Metal doesn't use VAOs
    }

    // --- Resource statistics ---

    @Override
    public int getBufferCount() {
        return MetalBuffer.getCount();
    }

    @Override
    public long getBufferTotalSize() {
        return MetalBuffer.getTotalSize();
    }

    @Override
    public int getTextureCount() {
        return MetalTexture.getCount();
    }

    @Override
    public long getTextureEstimatedTotalSize() {
        return MetalTexture.getEstimatedTotalSize();
    }

    @Override
    public void memoryBarrier(int flags) {
        // Metal performs automatic hazard tracking between encoders by default,
        // so most glMemoryBarrier bits are implicit. Explicit fences are needed
        // only for untracked resources, which we don't currently create.
    }

    @Override
    public void copyBufferSubData(IGpuBuffer src, IGpuBuffer dst, long srcOffset, long dstOffset, long size) {
        if (!(src instanceof MetalBuffer) || !(dst instanceof MetalBuffer)) {
            throw new IllegalArgumentException("copyBufferSubData on Metal backend requires MetalBuffer arguments");
        }
        enqueueBufferCopy(((MetalBuffer) src).getHandle(), ((MetalBuffer) dst).getHandle(),
                srcOffset, dstOffset, size);
    }

    @Override
    public void copyBufferSubData(IGpuPersistentBuffer src, IGpuBuffer dst, long srcOffset, long dstOffset, long size) {
        if (!(src instanceof MetalPersistentBuffer) || !(dst instanceof MetalBuffer)) {
            throw new IllegalArgumentException("copyBufferSubData on Metal backend requires Metal buffer arguments");
        }
        enqueueBufferCopy(((MetalPersistentBuffer) src).getHandle(), ((MetalBuffer) dst).getHandle(),
                srcOffset, dstOffset, size);
    }

    @Override
    public void copyBufferSubData(IGpuBuffer src, IGpuPersistentBuffer dst, long srcOffset, long dstOffset, long size) {
        if (!(src instanceof MetalBuffer) || !(dst instanceof MetalPersistentBuffer)) {
            throw new IllegalArgumentException("copyBufferSubData on Metal backend requires Metal buffer arguments");
        }
        enqueueBufferCopy(((MetalBuffer) src).getHandle(), ((MetalPersistentBuffer) dst).getHandle(),
                srcOffset, dstOffset, size);
    }

    private void enqueueBufferCopy(long srcHandle, long dstHandle, long srcOffset, long dstOffset, long size) {
        if (size <= 0) return;
        long cmdBuf = MetalNative.mtlCommandQueueNewCommandBuffer(this.commandQueue);
        long blit = MetalNative.mtlCommandBufferNewBlitEncoder(cmdBuf);
        MetalNative.mtlBlitEncoderCopyBuffer(blit, srcHandle, srcOffset, dstHandle, dstOffset, size);
        MetalNative.mtlEncoderEndEncoding(blit);
        MetalNative.mtlRelease(blit);
        MetalNative.mtlCommandBufferCommit(cmdBuf);
        MetalNative.mtlRelease(cmdBuf);
    }

    // --- Metal-specific accessors ---

    public long getDevice() {
        return this.device;
    }

    public long getCommandQueue() {
        return this.commandQueue;
    }

    public long getSharedEvent() {
        return this.sharedEvent;
    }

    public long newCommandBuffer() {
        return MetalNative.mtlCommandQueueNewCommandBuffer(this.commandQueue);
    }

    public void shutdown() {
        MetalNative.mtlRelease(this.sharedEvent);
        MetalNative.mtlRelease(this.commandQueue);
        MetalNative.mtlRelease(this.device);
        Logger.info("Metal backend shut down");
    }

    // --- Render pass encoding ---

    @Override
    public RenderEncoder beginRenderPass(RenderPassDesc desc) {
        long passDescHandle = MetalNative.mtlNewRenderPassDescriptor();
        if (passDescHandle == 0) {
            throw new RuntimeException("mtlNewRenderPassDescriptor returned NULL");
        }
        long encoder;
        try {
            for (int i = 0; i < desc.colorAttachments().size(); i++) {
                RenderPassDesc.ColorAttachment c = desc.colorAttachments().get(i);
                long texHandle = MetalHandleMap.getHandle(c.texture().id());
                MetalNative.mtlRenderPassSetColorAttachment(passDescHandle, i,
                        texHandle, mapLoadAction(c.loadAction()),
                        mapStoreAction(c.storeAction()), c.level());
                if (c.loadAction() == RenderPassDesc.LoadAction.CLEAR) {
                    MetalNative.mtlRenderPassSetColorClearColor(passDescHandle, i,
                            c.clearR(), c.clearG(), c.clearB(), c.clearA());
                }
            }
            if (desc.depthAttachment() != null) {
                RenderPassDesc.DepthAttachment d = desc.depthAttachment();
                long texHandle = MetalHandleMap.getHandle(d.texture().id());
                MetalNative.mtlRenderPassSetDepthAttachment(passDescHandle, texHandle,
                        mapLoadAction(d.loadAction()), mapStoreAction(d.storeAction()),
                        d.clearDepth(), d.level());
            }

            if (this.activeCommandBuffer == 0) {
                this.activeCommandBuffer = MetalNative.mtlCommandQueueNewCommandBuffer(this.commandQueue);
                if (this.activeCommandBuffer == 0) {
                    throw new RuntimeException("mtlCommandQueueNewCommandBuffer returned NULL");
                }
            }

            encoder = MetalNative.mtlCommandBufferNewRenderEncoder(this.activeCommandBuffer, passDescHandle);
            if (encoder == 0) {
                throw new RuntimeException("mtlCommandBufferNewRenderEncoder returned NULL");
            }
        } finally {
            // The render encoder retains a reference to the descriptor; we can drop ours.
            MetalNative.mtlRelease(passDescHandle);
        }

        return new MetalRenderEncoder(encoder);
    }

    @Override
    public IGpuPipeline createGraphicsPipeline(GraphicsPipelineDesc desc) {
        // If MSL isn't pre-baked, fall back to the runtime GLSL → SPIRV → MSL
        // pipeline. M9 migrated call sites supply GLSL only (one source, all
        // backends); only the M3-M8 smoke tests feed pre-baked MSL/SPIRV.
        String vertexMsl = desc.vertexMsl;
        String fragmentMsl = desc.fragmentMsl;
        if (vertexMsl == null || fragmentMsl == null) {
            if (desc.vertexGlsl == null || desc.fragmentGlsl == null) {
                throw new IllegalArgumentException(
                        "MetalRenderBackend.createGraphicsPipeline: need MSL or GLSL; got neither. label=" + desc.label);
            }
            try {
                var vertResult = me.cortex.voxy.client.core.gpu.shader.RuntimeShaderCompiler.compile(
                        desc.vertexGlsl,
                        me.cortex.voxy.client.core.gpu.shader.RuntimeShaderCompiler.Stage.VERTEX,
                        desc.defines,
                        me.cortex.voxy.client.core.gpu.shader.RuntimeShaderCompiler.Target.METAL_MSL);
                var fragResult = me.cortex.voxy.client.core.gpu.shader.RuntimeShaderCompiler.compile(
                        desc.fragmentGlsl,
                        me.cortex.voxy.client.core.gpu.shader.RuntimeShaderCompiler.Stage.FRAGMENT,
                        desc.defines,
                        me.cortex.voxy.client.core.gpu.shader.RuntimeShaderCompiler.Target.METAL_MSL);
                vertexMsl = vertResult.mslSource();
                fragmentMsl = fragResult.mslSource();
            } catch (Throwable t) {
                throw new RuntimeException(
                        "MetalRenderBackend.createGraphicsPipeline: GLSL → MSL transpile failed for label="
                                + desc.label, t);
            }
        }

        long vertexLib = MetalNative.mtlDeviceNewLibraryWithSource(this.device, vertexMsl);
        if (vertexLib == 0) {
            throw new RuntimeException("Vertex MSL compile failed: " + MetalNative.mtlGetLastCompileError());
        }
        long fragmentLib = 0;
        long vertexFn = 0;
        long fragmentFn = 0;
        long pipelineState = 0;
        long pipelineDesc = 0;
        long dssHandle = 0;
        try {
            fragmentLib = MetalNative.mtlDeviceNewLibraryWithSource(this.device, fragmentMsl);
            if (fragmentLib == 0) {
                throw new RuntimeException("Fragment MSL compile failed: " + MetalNative.mtlGetLastCompileError());
            }
            vertexFn = MetalNative.mtlLibraryNewFunction(vertexLib, "main0");
            if (vertexFn == 0) {
                // SPIRV-Cross emits the entry point as "main0" by default; fall back to "main".
                vertexFn = MetalNative.mtlLibraryNewFunction(vertexLib, "main");
                if (vertexFn == 0) {
                    throw new RuntimeException("Vertex function 'main0'/'main' not found in compiled library");
                }
            }
            fragmentFn = MetalNative.mtlLibraryNewFunction(fragmentLib, "main0");
            if (fragmentFn == 0) {
                fragmentFn = MetalNative.mtlLibraryNewFunction(fragmentLib, "main");
                if (fragmentFn == 0) {
                    throw new RuntimeException("Fragment function 'main0'/'main' not found in compiled library");
                }
            }

            // colorAttachmentFormat == 0 means "depth-only render pass — no
            // color target" (e.g. HiZBuffer's blit pipeline writes only
            // gl_FragDepth). Skip the color attachment format entirely in
            // that case; Metal accepts a pipeline with no color attachments
            // as long as the fragment shader doesn't write any.
            pipelineDesc = MetalNative.mtlNewRenderPipelineDescriptor();
            if (pipelineDesc == 0) {
                throw new RuntimeException("mtlNewRenderPipelineDescriptor returned NULL");
            }
            MetalNative.mtlRenderPipelineDescriptorSetVertexFunction(pipelineDesc, vertexFn);
            MetalNative.mtlRenderPipelineDescriptorSetFragmentFunction(pipelineDesc, fragmentFn);
            if (desc.colorAttachmentFormat != 0) {
                int metalPixelFormat = MetalFormatUtil.glFormatToMetal(desc.colorAttachmentFormat);
                MetalNative.mtlRenderPipelineDescriptorSetColorAttachmentFormat(pipelineDesc, 0, metalPixelFormat);
            }
            // Blocker 1: enable ICB usage on every pipeline. The only Metal
            // features that conflict (vertex amplification, function constants
            // on stage-input) aren't used anywhere in Voxy. The runtime cost
            // is the validation overhead Metal adds at draw time; benchmarks
            // (M14) can re-evaluate gating this if it shows up as overhead.
            // ICB-incompatible shaders (notably fragment shaders that write
            // gl_FragDepth or use certain outputs) reject the flag with
            // "Fragment shader cannot be used with indirect command buffers".
            // Only the MDIC terrain pipelines actually need ICB execution, so
            // the flag is opt-in via the desc.
            if (desc.usedInIndirectCommandBuffer) {
                MetalNative.mtlRenderPipelineDescriptorSetSupportIndirectCommandBuffers(pipelineDesc, true);
            }
            // Always declare a depth attachment format. Some Voxy fragment
            // shaders (depth0.frag, depth_copy.frag, blit.fsh, hiz/blit.fsh,
            // and the patched terrain frags) write to gl_FragDepth — Metal
            // rejects the pipeline with "depthAttachmentPixelFormat is not
            // valid" unless the descriptor has a depth format set. Use
            // MTLPixelFormatDepth32Float (universally supported on Apple
            // Silicon; the equivalent of Voxy's most common depth target).
            // Pipelines that don't actually write depth pay no penalty here.
            MetalNative.mtlRenderPipelineDescriptorSetDepthAttachmentPixelFormat(
                    pipelineDesc, MetalFormatUtil.MTLPixelFormatDepth32Float);

            // Bake blend state into the pipeline (Metal stores it on the pipeline,
            // not on the encoder). Skip when there's no color attachment.
            if (desc.colorAttachmentFormat != 0) {
                PipelineState.BlendState blend = desc.state.blend;
                MetalNative.mtlRenderPipelineDescriptorSetColorAttachmentBlending(pipelineDesc, 0,
                        blend.enabled,
                        mapBlendOp(blend.colorOp), mapBlendOp(blend.alphaOp),
                        mapBlendFactor(blend.srcColor), mapBlendFactor(blend.dstColor),
                        mapBlendFactor(blend.srcAlpha), mapBlendFactor(blend.dstAlpha));
            }

            // Build + attach vertex descriptor if the pipeline declares vertex inputs.
            // Empty layout → no descriptor (gl_VertexIndex-driven shaders).
            long vertexDescHandle = 0;
            if (desc.vertexLayout.attributes.length > 0 || desc.vertexLayout.buffers.length > 0) {
                vertexDescHandle = MetalNative.mtlNewVertexDescriptor();
                if (vertexDescHandle == 0) {
                    throw new RuntimeException("mtlNewVertexDescriptor returned NULL");
                }
                for (VertexLayout.VertexAttribute attr : desc.vertexLayout.attributes) {
                    MetalNative.mtlVertexDescriptorSetAttribute(vertexDescHandle,
                            attr.location, attr.format.metalValue, attr.offset, attr.bufferSlot);
                }
                for (VertexLayout.VertexBufferBinding buf : desc.vertexLayout.buffers) {
                    int stepFunction = buf.stepRate == VertexLayout.StepRate.PER_INSTANCE
                            ? MetalNative.MTLVertexStepFunctionPerInstance
                            : MetalNative.MTLVertexStepFunctionPerVertex;
                    MetalNative.mtlVertexDescriptorSetLayout(vertexDescHandle,
                            buf.slot, buf.stride, stepFunction, 1);
                }
                MetalNative.mtlRenderPipelineDescriptorSetVertexDescriptor(pipelineDesc, vertexDescHandle);
            }

            pipelineState = MetalNative.mtlDeviceNewRenderPipelineState(this.device, pipelineDesc);
            if (vertexDescHandle != 0) {
                // Pipeline state retained the descriptor; drop our reference.
                MetalNative.mtlRelease(vertexDescHandle);
            }

            // Build the depth-stencil state. Skip if both test + write are off.
            if (desc.state.depth.testEnabled || desc.state.depth.writeEnabled) {
                long dssDesc = MetalNative.mtlNewDepthStencilDescriptor();
                if (dssDesc == 0) throw new RuntimeException("mtlNewDepthStencilDescriptor returned NULL");
                try {
                    int compareFn = desc.state.depth.testEnabled
                            ? mapCompareOp(desc.state.depth.compareOp)
                            : MetalNative.MTLCompareFunctionAlways;
                    MetalNative.mtlDepthStencilDescriptorSetCompareFunction(dssDesc, compareFn);
                    MetalNative.mtlDepthStencilDescriptorSetDepthWriteEnabled(dssDesc, desc.state.depth.writeEnabled);
                    dssHandle = MetalNative.mtlDeviceNewDepthStencilState(this.device, dssDesc);
                    if (dssHandle == 0) throw new RuntimeException("mtlDeviceNewDepthStencilState returned NULL");
                } finally {
                    MetalNative.mtlRelease(dssDesc);
                }
            }
            int cullModeI = mapCullMode(desc.state.raster.cullMode);
            int windingI = mapFrontFace(desc.state.raster.frontFace);
            int fillModeI = mapPolygonMode(desc.state.raster.polygonMode);
            if (pipelineState == 0) {
                throw new RuntimeException("Pipeline state link failed: " + MetalNative.mtlGetLastCompileError());
            }
            if (desc.label != null) {
                MetalNative.mtlSetLabel(pipelineState, desc.label);
            }

            MetalGraphicsPipeline result = new MetalGraphicsPipeline(
                    pipelineState, vertexLib, fragmentLib, vertexFn, fragmentFn,
                    dssHandle, cullModeI, windingI, fillModeI);
            // result owns the handles now; clear locals so the catch path doesn't double-release.
            pipelineState = 0;
            vertexFn = 0;
            fragmentFn = 0;
            vertexLib = 0;
            fragmentLib = 0;
            dssHandle = 0;
            return result;
        } finally {
            if (pipelineDesc != 0) MetalNative.mtlRelease(pipelineDesc);
            if (pipelineState != 0) MetalNative.mtlRelease(pipelineState);
            if (dssHandle != 0) MetalNative.mtlRelease(dssHandle);
            if (fragmentFn != 0) MetalNative.mtlRelease(fragmentFn);
            if (vertexFn != 0) MetalNative.mtlRelease(vertexFn);
            if (fragmentLib != 0) MetalNative.mtlRelease(fragmentLib);
            if (vertexLib != 0) MetalNative.mtlRelease(vertexLib);
        }
    }

    // --- PipelineState → Metal enum translators ---

    private static int mapCompareOp(PipelineState.CompareOp op) {
        return switch (op) {
            case NEVER -> MetalNative.MTLCompareFunctionNever;
            case LESS -> MetalNative.MTLCompareFunctionLess;
            case EQUAL -> MetalNative.MTLCompareFunctionEqual;
            case LESS_EQUAL -> MetalNative.MTLCompareFunctionLessEqual;
            case GREATER -> MetalNative.MTLCompareFunctionGreater;
            case NOT_EQUAL -> MetalNative.MTLCompareFunctionNotEqual;
            case GREATER_EQUAL -> MetalNative.MTLCompareFunctionGreaterEqual;
            case ALWAYS -> MetalNative.MTLCompareFunctionAlways;
        };
    }

    private static int mapCullMode(PipelineState.CullMode mode) {
        return switch (mode) {
            case NONE -> MetalNative.MTLCullModeNone;
            case FRONT -> MetalNative.MTLCullModeFront;
            case BACK -> MetalNative.MTLCullModeBack;
        };
    }

    private static int mapFrontFace(PipelineState.FrontFace face) {
        return switch (face) {
            case CLOCKWISE -> MetalNative.MTLWindingClockwise;
            case COUNTER_CLOCKWISE -> MetalNative.MTLWindingCounterClockwise;
        };
    }

    private static int mapPolygonMode(PipelineState.PolygonMode mode) {
        return switch (mode) {
            case FILL -> MetalNative.MTLTriangleFillModeFill;
            case LINE -> MetalNative.MTLTriangleFillModeLines;
        };
    }

    private static int mapBlendOp(PipelineState.BlendOp op) {
        return switch (op) {
            case ADD -> MetalNative.MTLBlendOperationAdd;
            case SUBTRACT -> MetalNative.MTLBlendOperationSubtract;
            case REVERSE_SUBTRACT -> MetalNative.MTLBlendOperationReverseSubtract;
            case MIN -> MetalNative.MTLBlendOperationMin;
            case MAX -> MetalNative.MTLBlendOperationMax;
        };
    }

    private static int mapSamplerFilter(SamplerDesc.Filter f) {
        return switch (f) {
            case NEAREST -> MetalNative.MTLSamplerFilterNearest;
            case LINEAR -> MetalNative.MTLSamplerFilterLinear;
        };
    }

    private static int mapSamplerMipFilter(SamplerDesc.MipFilter f) {
        return switch (f) {
            case NOT_MIPMAPPED -> MetalNative.MTLSamplerMipFilterNotMipmapped;
            case NEAREST -> MetalNative.MTLSamplerMipFilterNearest;
            case LINEAR -> MetalNative.MTLSamplerMipFilterLinear;
        };
    }

    private static int mapSamplerWrap(SamplerDesc.Wrap w) {
        return switch (w) {
            case CLAMP_TO_EDGE -> MetalNative.MTLSamplerAddressModeClampToEdge;
            case REPEAT -> MetalNative.MTLSamplerAddressModeRepeat;
            case MIRRORED_REPEAT -> MetalNative.MTLSamplerAddressModeMirrorRepeat;
            case CLAMP_TO_ZERO -> MetalNative.MTLSamplerAddressModeClampToZero;
        };
    }

    private static int mapBlendFactor(PipelineState.BlendFactor f) {
        return switch (f) {
            case ZERO -> MetalNative.MTLBlendFactorZero;
            case ONE -> MetalNative.MTLBlendFactorOne;
            case SRC_COLOR -> MetalNative.MTLBlendFactorSourceColor;
            case ONE_MINUS_SRC_COLOR -> MetalNative.MTLBlendFactorOneMinusSourceColor;
            case DST_COLOR -> MetalNative.MTLBlendFactorDestinationColor;
            case ONE_MINUS_DST_COLOR -> MetalNative.MTLBlendFactorOneMinusDestinationColor;
            case SRC_ALPHA -> MetalNative.MTLBlendFactorSourceAlpha;
            case ONE_MINUS_SRC_ALPHA -> MetalNative.MTLBlendFactorOneMinusSourceAlpha;
            case DST_ALPHA -> MetalNative.MTLBlendFactorDestinationAlpha;
            case ONE_MINUS_DST_ALPHA -> MetalNative.MTLBlendFactorOneMinusDestinationAlpha;
        };
    }

    @Override
    public void submit() {
        if (this.activeCommandBuffer == 0) return;
        MetalNative.mtlCommandBufferCommit(this.activeCommandBuffer);
        // Sync mode for M3: wait for completion so the smoke test can check status
        // before the buffer is released. M5+ will move to async + per-frame fences.
        MetalNative.mtlCommandBufferWaitUntilCompleted(this.activeCommandBuffer);
        int status = MetalNative.mtlCommandBufferGetStatus(this.activeCommandBuffer);
        MetalNative.mtlRelease(this.activeCommandBuffer);
        this.activeCommandBuffer = 0;
        if (status != MetalNative.MTLCommandBufferStatusCompleted) {
            throw new RuntimeException("Metal command buffer ended with status=" + status
                    + " (expected " + MetalNative.MTLCommandBufferStatusCompleted + " = Completed)");
        }
    }

    @Override
    public IGpuPipeline createComputePipeline(ComputePipelineDesc desc) {
        String computeMsl = desc.computeMsl;
        if (computeMsl == null) {
            if (desc.computeGlsl == null) {
                throw new IllegalArgumentException(
                        "createComputePipeline: need MSL or GLSL; got neither. label=" + desc.label);
            }
            try {
                var result = me.cortex.voxy.client.core.gpu.shader.RuntimeShaderCompiler.compile(
                        desc.computeGlsl,
                        me.cortex.voxy.client.core.gpu.shader.RuntimeShaderCompiler.Stage.COMPUTE,
                        desc.defines,
                        me.cortex.voxy.client.core.gpu.shader.RuntimeShaderCompiler.Target.METAL_MSL);
                computeMsl = result.mslSource();
            } catch (Throwable t) {
                throw new RuntimeException(
                        "MetalRenderBackend.createComputePipeline: GLSL → MSL transpile failed for label="
                                + desc.label, t);
            }
        }
        long library = MetalNative.mtlDeviceNewLibraryWithSource(this.device, computeMsl);
        if (library == 0) {
            throw new RuntimeException("Compute MSL compile failed: " + MetalNative.mtlGetLastCompileError());
        }
        long function = 0;
        long pipelineState = 0;
        try {
            function = MetalNative.mtlLibraryNewFunction(library, "main0");
            if (function == 0) {
                function = MetalNative.mtlLibraryNewFunction(library, "main");
                if (function == 0) {
                    throw new RuntimeException("Compute function 'main0'/'main' not found in compiled library");
                }
            }
            pipelineState = MetalNative.mtlDeviceNewComputePipelineState(this.device, function);
            if (pipelineState == 0) {
                throw new RuntimeException("Compute pipeline state link failed: " + MetalNative.mtlGetLastCompileError());
            }
            if (desc.label != null) {
                MetalNative.mtlSetLabel(pipelineState, desc.label);
            }

            MetalComputePipeline result = new MetalComputePipeline(
                    pipelineState, library, function,
                    desc.localSizeX, desc.localSizeY, desc.localSizeZ);
            pipelineState = 0;
            function = 0;
            library = 0;
            return result;
        } finally {
            if (pipelineState != 0) MetalNative.mtlRelease(pipelineState);
            if (function != 0) MetalNative.mtlRelease(function);
            if (library != 0) MetalNative.mtlRelease(library);
        }
    }

    @Override
    public IGpuSampler createSampler(SamplerDesc desc) {
        long descHandle = MetalNative.mtlNewSamplerDescriptor();
        if (descHandle == 0) throw new RuntimeException("mtlNewSamplerDescriptor returned NULL");
        try {
            MetalNative.mtlSamplerDescriptorSetMinFilter(descHandle, mapSamplerFilter(desc.minFilter));
            MetalNative.mtlSamplerDescriptorSetMagFilter(descHandle, mapSamplerFilter(desc.magFilter));
            MetalNative.mtlSamplerDescriptorSetMipFilter(descHandle, mapSamplerMipFilter(desc.mipFilter));
            MetalNative.mtlSamplerDescriptorSetSAddressMode(descHandle, mapSamplerWrap(desc.wrapS));
            MetalNative.mtlSamplerDescriptorSetTAddressMode(descHandle, mapSamplerWrap(desc.wrapT));
            MetalNative.mtlSamplerDescriptorSetRAddressMode(descHandle, mapSamplerWrap(desc.wrapR));
            MetalNative.mtlSamplerDescriptorSetLodMinClamp(descHandle, desc.lodMinClamp);
            MetalNative.mtlSamplerDescriptorSetLodMaxClamp(descHandle, desc.lodMaxClamp);
            int compareFn = desc.compareEnable
                    ? mapCompareOp(desc.compareOp)
                    : MetalNative.MTLCompareFunctionNever;
            MetalNative.mtlSamplerDescriptorSetCompareFunction(descHandle, compareFn);

            long stateHandle = MetalNative.mtlDeviceNewSamplerState(this.device, descHandle);
            if (stateHandle == 0) throw new RuntimeException("mtlDeviceNewSamplerState returned NULL");
            if (desc.label != null) MetalNative.mtlSetLabel(stateHandle, desc.label);
            return new MetalSampler(stateHandle);
        } finally {
            MetalNative.mtlRelease(descHandle);
        }
    }

    @Override
    public ComputeEncoder beginComputePass() {
        if (this.activeCommandBuffer == 0) {
            this.activeCommandBuffer = MetalNative.mtlCommandQueueNewCommandBuffer(this.commandQueue);
            if (this.activeCommandBuffer == 0) {
                throw new RuntimeException("mtlCommandQueueNewCommandBuffer returned NULL");
            }
        }
        long encoder = MetalNative.mtlCommandBufferNewComputeEncoder(this.activeCommandBuffer);
        if (encoder == 0) {
            throw new RuntimeException("mtlCommandBufferNewComputeEncoder returned NULL");
        }
        return new MetalComputeEncoder(encoder);
    }

    @Override
    public me.cortex.voxy.client.core.gpu.IGpuIndirectCommandBuffer createIndirectCommandBuffer(int maxCommands) {
        if (maxCommands <= 0) {
            throw new IllegalArgumentException("maxCommands must be > 0, got " + maxCommands);
        }
        return new MetalIndirectCommandBuffer(this.device, maxCommands);
    }

    /**
     * Synchronously read RGBA8 pixels from a texture region into a byte array.
     * Allocates a transient Shared-storage MTLBuffer, runs a blit-encoder copy,
     * waits for completion, and copies the contents back into the JVM heap.
     *
     * Used by the M5 smoke test to verify a triangle actually rasterized over
     * the cleared background; will likely also serve M14 benchmark capture.
     * Not part of {@link RenderBackend} yet — backend-agnostic readback lands
     * in M9 with the migration of GLPixelDownload through the abstraction.
     */
    public byte[] readPixelsRGBA8(me.cortex.voxy.client.core.gpu.IGpuTexture texture,
                                   int x, int y, int width, int height) {
        final int bytesPerPixel = 4;
        final int bytesPerRow = width * bytesPerPixel;
        final long bufferSize = (long) bytesPerRow * height;

        long readbackBuf = MetalNative.mtlDeviceNewBuffer(this.device, bufferSize,
                MetalNative.MTLResourceStorageModeShared);
        if (readbackBuf == 0) {
            throw new RuntimeException("readPixelsRGBA8: failed to allocate readback buffer ("
                    + bufferSize + " bytes)");
        }
        long cmdBuf = 0;
        long blitEnc = 0;
        try {
            cmdBuf = MetalNative.mtlCommandQueueNewCommandBuffer(this.commandQueue);
            if (cmdBuf == 0) throw new RuntimeException("readPixelsRGBA8: failed to allocate command buffer");
            blitEnc = MetalNative.mtlCommandBufferNewBlitEncoder(cmdBuf);
            if (blitEnc == 0) throw new RuntimeException("readPixelsRGBA8: failed to allocate blit encoder");

            long texHandle = MetalHandleMap.getHandle(texture.id());
            MetalNative.mtlBlitEncoderCopyTextureToBuffer(blitEnc, texHandle, 0,
                    x, y, width, height,
                    readbackBuf, 0, bytesPerRow, (int) bufferSize);
            MetalNative.mtlEncoderEndEncoding(blitEnc);
            MetalNative.mtlRelease(blitEnc);
            blitEnc = 0;

            MetalNative.mtlCommandBufferCommit(cmdBuf);
            MetalNative.mtlCommandBufferWaitUntilCompleted(cmdBuf);
            int status = MetalNative.mtlCommandBufferGetStatus(cmdBuf);
            if (status != MetalNative.MTLCommandBufferStatusCompleted) {
                throw new RuntimeException("readPixelsRGBA8 blit ended with status=" + status);
            }

            long contentsPtr = MetalNative.mtlBufferContents(readbackBuf);
            if (contentsPtr == 0) throw new RuntimeException("readPixelsRGBA8: buffer contents pointer is null");
            byte[] result = new byte[(int) bufferSize];
            org.lwjgl.system.MemoryUtil.memByteBuffer(contentsPtr, result.length).get(result);
            return result;
        } finally {
            if (blitEnc != 0) MetalNative.mtlRelease(blitEnc);
            if (cmdBuf != 0) MetalNative.mtlRelease(cmdBuf);
            MetalNative.mtlRelease(readbackBuf);
        }
    }

    private static int mapLoadAction(RenderPassDesc.LoadAction action) {
        return switch (action) {
            case LOAD -> MetalNative.MTLLoadActionLoad;
            case CLEAR -> MetalNative.MTLLoadActionClear;
            case DONT_CARE -> MetalNative.MTLLoadActionDontCare;
        };
    }

    private static int mapStoreAction(RenderPassDesc.StoreAction action) {
        return switch (action) {
            case STORE -> MetalNative.MTLStoreActionStore;
            case DONT_CARE -> MetalNative.MTLStoreActionDontCare;
        };
    }
}
