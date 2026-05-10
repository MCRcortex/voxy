package me.cortex.voxy.client.core.metal;

import me.cortex.voxy.common.Logger;

/**
 * JNI bridge to the native Metal API via Objective-C runtime.
 *
 * All Metal operations are performed through this class. The native library
 * (libvoxy_metal.dylib) must be loaded before any calls are made.
 *
 * Metal concept mapping:
 *   - MTLDevice           → device handle (long pointer)
 *   - MTLCommandQueue     → command queue handle
 *   - MTLBuffer           → buffer handle
 *   - MTLTexture          → texture handle
 *   - MTLRenderPassDesc   → framebuffer equivalent
 *   - MTLEvent            → fence/sync primitive
 *   - MTLVertexDescriptor → vertex array equivalent
 *   - MTLLibrary          → shader library (compiled MSL)
 *   - MTLRenderPipelineState → pipeline state object
 *   - MTLComputePipelineState → compute pipeline state
 *
 * All handles are opaque pointers returned as long values.
 * A handle value of 0 (NULL) indicates an error.
 */
public final class MetalNative {

    private static boolean loaded = false;
    private static boolean available = false;

    private MetalNative() {}

    /**
     * Attempts to load the native Metal library.
     *
     * Load order:
     *   1. System.loadLibrary("voxy_metal") — honors java.library.path, useful
     *      for dev builds where the dylib sits next to the repo.
     *   2. Extract /natives/macos-arm64/libvoxy_metal.dylib from the mod jar
     *      into a temp file and System.load() that.
     *
     * Returns true if Metal is available on this platform.
     */
    public static synchronized boolean load() {
        if (loaded) return available;
        loaded = true;

        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (!(os.contains("mac") && arch.contains("aarch64"))) {
            Logger.info("Metal backend skipped: requires macOS aarch64 (got " + os + "/" + arch + ")");
            available = false;
            return false;
        }

        try {
            System.loadLibrary("voxy_metal");
            available = true;
            Logger.info("Metal native library loaded from java.library.path");
            return true;
        } catch (UnsatisfiedLinkError ignored) {
            // Fall through to resource extraction.
        }

        try {
            java.nio.file.Path tmp = extractResourceToTemp(
                    "/natives/macos-arm64/libvoxy_metal.dylib", "libvoxy_metal", ".dylib");
            if (tmp == null) {
                Logger.warn("Metal native library not bundled in jar; falling back to OpenGL");
                available = false;
                return false;
            }
            System.load(tmp.toAbsolutePath().toString());
            available = true;
            Logger.info("Metal native library loaded from " + tmp);
            return true;
        } catch (Throwable t) {
            Logger.warn("Metal native library not available: " + t.getMessage());
            available = false;
            return false;
        }
    }

    private static java.nio.file.Path extractResourceToTemp(
            String resourcePath, String prefix, String suffix) throws java.io.IOException {
        try (java.io.InputStream in = MetalNative.class.getResourceAsStream(resourcePath)) {
            if (in == null) return null;
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile(prefix, suffix);
            tmp.toFile().deleteOnExit();
            java.nio.file.Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return tmp;
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    // ========== Device & Command Queue ==========

    /** Creates the default MTLDevice. Returns device handle or 0 on failure. */
    public static native long mtlCreateSystemDefaultDevice();

    /** Releases a Metal object (decrements retain count). */
    public static native void mtlRelease(long handle);

    /** Retains a Metal object (increments retain count). */
    public static native void mtlRetain(long handle);

    /** Creates a command queue from a device. Returns queue handle. */
    public static native long mtlDeviceNewCommandQueue(long device);

    /** Gets the device name as a string. */
    public static native String mtlDeviceGetName(long device);

    /** Gets the maximum buffer length supported by the device. */
    public static native long mtlDeviceMaxBufferLength(long device);

    /** Checks if the device supports a specific GPU family (e.g., Apple7, Apple8). */
    public static native boolean mtlDeviceSupportsFamily(long device, int family);

    // ========== Buffer Operations ==========

    /**
     * Creates a new MTLBuffer.
     * @param device device handle
     * @param size buffer size in bytes
     * @param options resource options (storage mode, CPU cache mode, hazard tracking)
     * @return buffer handle or 0 on failure
     */
    public static native long mtlDeviceNewBuffer(long device, long size, int options);

    /**
     * Creates a new MTLBuffer initialized with data.
     * @param device device handle
     * @param dataAddr CPU address of source data
     * @param size data size in bytes
     * @param options resource options
     * @return buffer handle
     */
    public static native long mtlDeviceNewBufferWithData(long device, long dataAddr, long size, int options);

    /** Returns the CPU-accessible pointer for a buffer (shared/managed storage mode). */
    public static native long mtlBufferContents(long buffer);

    /** Returns the length (size in bytes) of the buffer. */
    public static native long mtlBufferLength(long buffer);

    /** Notifies Metal that a range of a managed buffer has been modified on CPU. */
    public static native void mtlBufferDidModifyRange(long buffer, long offset, long length);

    /** Sets a debug label on a Metal resource. */
    public static native void mtlSetLabel(long handle, String label);

    // ========== Texture Operations ==========

    /**
     * Creates a texture descriptor.
     * @param textureType e.g. MTLTextureType2D = 2
     * @param pixelFormat e.g. MTLPixelFormatRGBA8Unorm = 70
     * @param width texture width
     * @param height texture height
     * @param mipmapLevels number of mipmap levels
     * @param usage texture usage flags
     * @param storageMode storage mode
     * @return descriptor handle (must be released)
     */
    public static native long mtlNewTextureDescriptor(
            int textureType, int pixelFormat, int width, int height,
            int mipmapLevels, int usage, int storageMode);

    /**
     * Creates a texture from a descriptor.
     * @param device device handle
     * @param descriptor texture descriptor handle
     * @return texture handle
     */
    public static native long mtlDeviceNewTexture(long device, long descriptor);

    /**
     * Creates a texture view (shares storage with source texture).
     * @param texture source texture handle
     * @param pixelFormat new pixel format for the view
     * @return texture view handle
     */
    public static native long mtlTextureNewView(long texture, int pixelFormat);

    /** Uploads pixel data to a region of a texture. */
    public static native void mtlTextureReplaceRegion(
            long texture, int level,
            int x, int y, int width, int height,
            long dataAddr, int bytesPerRow);

    /** Gets the width of a texture. */
    public static native int mtlTextureGetWidth(long texture);

    /** Gets the height of a texture. */
    public static native int mtlTextureGetHeight(long texture);

    /** Gets the pixel format of a texture. */
    public static native int mtlTextureGetPixelFormat(long texture);

    /** Gets the mipmap level count of a texture. */
    public static native int mtlTextureGetMipmapLevelCount(long texture);

    /** Gets the texture type (1D, 2D, 3D, cube, etc.). */
    public static native int mtlTextureGetTextureType(long texture);

    // ========== Render Pass (Framebuffer equivalent) ==========

    /** Creates a new MTLRenderPassDescriptor. */
    public static native long mtlNewRenderPassDescriptor();

    /** Sets the texture for a color attachment. */
    public static native void mtlRenderPassSetColorAttachment(
            long descriptor, int index, long texture, int loadAction, int storeAction, int level);

    /** Sets the clear color for a color attachment (only honored when loadAction == Clear). */
    public static native void mtlRenderPassSetColorClearColor(
            long descriptor, int index, float r, float g, float b, float a);

    /** Sets the texture for the depth attachment. */
    public static native void mtlRenderPassSetDepthAttachment(
            long descriptor, long texture, int loadAction, int storeAction,
            float clearDepth, int level);

    /** Sets the texture for the stencil attachment. */
    public static native void mtlRenderPassSetStencilAttachment(
            long descriptor, long texture, int loadAction, int storeAction,
            int clearStencil, int level);

    // ========== Command Buffer & Encoding ==========

    /** Creates a new command buffer from a command queue. */
    public static native long mtlCommandQueueNewCommandBuffer(long queue);

    /** Commits a command buffer for execution. */
    public static native void mtlCommandBufferCommit(long cmdBuffer);

    /** Waits until the command buffer has completed execution. */
    public static native void mtlCommandBufferWaitUntilCompleted(long cmdBuffer);

    /** Gets the command buffer status (0=notEnqueued, 1=enqueued, 2=committed, 3=scheduled, 4=completed, 5=error). */
    public static native int mtlCommandBufferGetStatus(long cmdBuffer);

    /**
     * Creates a blit command encoder (for copy/fill operations).
     * @return encoder handle
     */
    public static native long mtlCommandBufferNewBlitEncoder(long cmdBuffer);

    /**
     * Creates a render command encoder bound to a render pass descriptor.
     * Load actions (incl. Clear) execute as the encoder is created.
     * @return encoder handle (must be released after endEncoding)
     */
    public static native long mtlCommandBufferNewRenderEncoder(long cmdBuffer, long renderPassDesc);

    // --- Render pipeline descriptor + state (M5) ---

    /** Creates a new MTLRenderPipelineDescriptor; returns handle (must be released). */
    public static native long mtlNewRenderPipelineDescriptor();

    /** Sets the vertex function (MTLFunction handle) on a render pipeline descriptor. */
    public static native void mtlRenderPipelineDescriptorSetVertexFunction(long descriptor, long function);

    /** Sets the fragment function (MTLFunction handle) on a render pipeline descriptor. */
    public static native void mtlRenderPipelineDescriptorSetFragmentFunction(long descriptor, long function);

    /** Sets the pixel format of a color attachment slot on a render pipeline descriptor. */
    public static native void mtlRenderPipelineDescriptorSetColorAttachmentFormat(
            long descriptor, int index, int pixelFormat);

    /**
     * Creates a MTLRenderPipelineState from a descriptor. On failure returns 0
     * and sets the last compile error retrievable via mtlGetLastCompileError.
     */
    public static native long mtlDeviceNewRenderPipelineState(long device, long descriptor);

    // --- Render encoder draw operations (M5) ---

    /** Sets the active render pipeline state on a render encoder. */
    public static native void mtlRenderEncoderSetRenderPipelineState(long encoder, long pipelineState);

    /**
     * Issue a non-indexed draw. primitiveType uses the same enum as MTLPrimitiveType
     * (0=Point, 1=Line, 2=LineStrip, 3=Triangle, 4=TriangleStrip).
     */
    public static native void mtlRenderEncoderDrawPrimitives(
            long encoder, int primitiveType,
            int firstVertex, int vertexCount,
            int instanceCount, int baseInstance);

    /**
     * Indexed draw via {@code drawIndexedPrimitives}. {@code indexType} is the Metal
     * MTLIndexType ordinal (0=UInt16, 1=UInt32).
     */
    public static native void mtlRenderEncoderDrawIndexedPrimitives(
            long encoder, int primitiveType,
            int indexCount, int indexType,
            long indexBuffer, long indexBufferOffset,
            int instanceCount, int baseVertex, int baseInstance);

    // --- Render encoder per-stage resource binding ---

    public static native void mtlRenderEncoderSetVertexBuffer(long encoder, long buffer, long offset, int index);
    public static native void mtlRenderEncoderSetFragmentBuffer(long encoder, long buffer, long offset, int index);
    public static native void mtlRenderEncoderSetVertexTexture(long encoder, long texture, int index);
    public static native void mtlRenderEncoderSetFragmentTexture(long encoder, long texture, int index);

    /** Set viewport. Origin is in pixel coordinates (top-left). */
    public static native void mtlRenderEncoderSetViewport(long encoder,
            double originX, double originY, double width, double height,
            double znear, double zfar);

    /** Set scissor rect in pixel coordinates. */
    public static native void mtlRenderEncoderSetScissorRect(long encoder,
            int x, int y, int width, int height);

    /** MTLIndexType values. */
    public static final int MTLIndexTypeUInt16 = 0;
    public static final int MTLIndexTypeUInt32 = 1;

    // --- Vertex descriptor (for graphics pipelines with vertex inputs) ---

    /** Creates an empty MTLVertexDescriptor. */
    public static native long mtlNewVertexDescriptor();

    /** Sets attribute N of a vertex descriptor (location, format, offset, bufferIndex). */
    public static native void mtlVertexDescriptorSetAttribute(
            long descriptor, int index, int format, long offset, int bufferIndex);

    /**
     * Sets buffer layout N of a vertex descriptor: stride, step function
     * (1=PerVertex, 2=PerInstance), step rate (usually 1).
     */
    public static native void mtlVertexDescriptorSetLayout(
            long descriptor, int bufferIndex, long stride, int stepFunction, int stepRate);

    /** Attaches a vertex descriptor to a render pipeline descriptor. */
    public static native void mtlRenderPipelineDescriptorSetVertexDescriptor(
            long pipelineDescriptor, long vertexDescriptor);

    /** MTLVertexStepFunction values. */
    public static final int MTLVertexStepFunctionPerVertex = 1;
    public static final int MTLVertexStepFunctionPerInstance = 2;

    // --- Indirect draw (single-draw-per-call; multi-draw is a CPU loop in MetalRenderEncoder) ---

    /**
     * Indirect non-indexed draw. {@code indirectBuffer} holds a struct of
     * (vertexCount, instanceCount, firstVertex, firstInstance) as 4 uint32 values
     * at byte offset {@code indirectOffset}.
     */
    public static native void mtlRenderEncoderDrawPrimitivesIndirect(
            long encoder, int primitiveType, long indirectBuffer, long indirectOffset);

    /**
     * Indirect indexed draw. {@code indirectBuffer} holds (indexCount, instanceCount,
     * firstIndex, vertexOffset, firstInstance) as 5 uint32 values at byte offset
     * {@code indirectOffset}. Caller must have set up the index buffer separately.
     */
    public static native void mtlRenderEncoderDrawIndexedPrimitivesIndirect(
            long encoder, int primitiveType, int indexType,
            long indexBuffer, long indexBufferOffset,
            long indirectBuffer, long indirectOffset);

    // --- Pipeline static state: depth-stencil + blend + raster ---

    /** Configures color attachment N's blend state on a render pipeline descriptor. */
    public static native void mtlRenderPipelineDescriptorSetColorAttachmentBlending(
            long descriptor, int index, boolean enable,
            int rgbOp, int alphaOp,
            int srcRgb, int dstRgb, int srcAlpha, int dstAlpha);

    public static native long mtlNewDepthStencilDescriptor();
    public static native void mtlDepthStencilDescriptorSetCompareFunction(long descriptor, int compareFunction);
    public static native void mtlDepthStencilDescriptorSetDepthWriteEnabled(long descriptor, boolean enabled);
    public static native long mtlDeviceNewDepthStencilState(long device, long descriptor);

    public static native void mtlRenderEncoderSetDepthStencilState(long encoder, long state);
    public static native void mtlRenderEncoderSetCullMode(long encoder, int cullMode);
    public static native void mtlRenderEncoderSetFrontFacingWinding(long encoder, int winding);
    public static native void mtlRenderEncoderSetTriangleFillMode(long encoder, int fillMode);

    /** MTLCompareFunction enum (Never=0 ... Always=7). */
    public static final int MTLCompareFunctionNever = 0;
    public static final int MTLCompareFunctionLess = 1;
    public static final int MTLCompareFunctionEqual = 2;
    public static final int MTLCompareFunctionLessEqual = 3;
    public static final int MTLCompareFunctionGreater = 4;
    public static final int MTLCompareFunctionNotEqual = 5;
    public static final int MTLCompareFunctionGreaterEqual = 6;
    public static final int MTLCompareFunctionAlways = 7;

    /** MTLCullMode enum. */
    public static final int MTLCullModeNone = 0;
    public static final int MTLCullModeFront = 1;
    public static final int MTLCullModeBack = 2;

    /** MTLWinding enum. */
    public static final int MTLWindingClockwise = 0;
    public static final int MTLWindingCounterClockwise = 1;

    /** MTLTriangleFillMode enum. */
    public static final int MTLTriangleFillModeFill = 0;
    public static final int MTLTriangleFillModeLines = 1;

    /** MTLBlendOperation enum. */
    public static final int MTLBlendOperationAdd = 0;
    public static final int MTLBlendOperationSubtract = 1;
    public static final int MTLBlendOperationReverseSubtract = 2;
    public static final int MTLBlendOperationMin = 3;
    public static final int MTLBlendOperationMax = 4;

    /** MTLBlendFactor enum (subset). */
    public static final int MTLBlendFactorZero = 0;
    public static final int MTLBlendFactorOne = 1;
    public static final int MTLBlendFactorSourceColor = 2;
    public static final int MTLBlendFactorOneMinusSourceColor = 3;
    public static final int MTLBlendFactorSourceAlpha = 4;
    public static final int MTLBlendFactorOneMinusSourceAlpha = 5;
    public static final int MTLBlendFactorDestinationColor = 6;
    public static final int MTLBlendFactorOneMinusDestinationColor = 7;
    public static final int MTLBlendFactorDestinationAlpha = 8;
    public static final int MTLBlendFactorOneMinusDestinationAlpha = 9;

    // --- Compute encoder (M7) ---

    /** Creates a compute command encoder on the active command buffer. */
    public static native long mtlCommandBufferNewComputeEncoder(long cmdBuffer);

    /** Sets the active MTLComputePipelineState on a compute encoder. */
    public static native void mtlComputeEncoderSetComputePipelineState(long encoder, long pipelineState);

    /** Binds a MTLBuffer at a given index for the active compute pipeline. */
    public static native void mtlComputeEncoderSetBuffer(long encoder, long buffer, long offset, int index);

    /**
     * Dispatch (gx, gy, gz) thread-groups, each containing (tx, ty, tz) threads.
     * tx/ty/tz must match the local_size_* declared in the compute shader.
     */
    public static native void mtlComputeEncoderDispatchThreadgroups(
            long encoder, int gx, int gy, int gz, int tx, int ty, int tz);

    /** Binds a texture at a given index for the active compute pipeline. */
    public static native void mtlComputeEncoderSetTexture(long encoder, long texture, int index);

    /**
     * Indirect dispatch: groupCountX/Y/Z come from three uint32 values at
     * (indirectBuffer + indirectOffset). Threads-per-threadgroup still has
     * to match the shader's local size.
     */
    public static native void mtlComputeEncoderDispatchThreadgroupsIndirect(
            long encoder, long indirectBuffer, long indirectOffset, int tx, int ty, int tz);

    /**
     * Memory barrier within a compute encoder.
     * Scope flags: 0x1 = MTLBarrierScopeBuffers, 0x2 = MTLBarrierScopeTextures,
     * 0x4 = MTLBarrierScopeRenderTargets.
     */
    public static native void mtlComputeEncoderMemoryBarrier(long encoder, int scope);

    // MTLBarrierScope (raw enum from Metal)
    public static final int MTLBarrierScopeBuffers = 0x1;
    public static final int MTLBarrierScopeTextures = 0x2;
    public static final int MTLBarrierScopeRenderTargets = 0x4;

    // --- Blit encoder readback (M5) ---

    /** Copies a region of a texture into a buffer (for CPU readback). */
    public static native void mtlBlitEncoderCopyTextureToBuffer(
            long encoder, long srcTexture, int srcLevel,
            int srcX, int srcY, int srcWidth, int srcHeight,
            long dstBuffer, long dstOffset, int bytesPerRow, int bytesPerImage);

    // --- Metal primitive types (match MTLPrimitiveType) ---
    public static final int MTLPrimitiveTypePoint         = 0;
    public static final int MTLPrimitiveTypeLine          = 1;
    public static final int MTLPrimitiveTypeLineStrip     = 2;
    public static final int MTLPrimitiveTypeTriangle      = 3;
    public static final int MTLPrimitiveTypeTriangleStrip = 4;

    /** Ends encoding on an encoder. */
    public static native void mtlEncoderEndEncoding(long encoder);

    /** Fills a buffer with a byte value using a blit encoder. */
    public static native void mtlBlitEncoderFillBuffer(
            long encoder, long buffer, long offset, long length, byte value);

    /** Copies data between buffers using a blit encoder. */
    public static native void mtlBlitEncoderCopyBuffer(
            long encoder, long srcBuffer, long srcOffset,
            long dstBuffer, long dstOffset, long size);

    /** Copies from buffer to texture using a blit encoder. */
    public static native void mtlBlitEncoderCopyBufferToTexture(
            long encoder, long srcBuffer, long srcOffset, int srcBytesPerRow,
            long dstTexture, int dstSlice, int dstLevel,
            int dstX, int dstY, int width, int height);

    // ========== Synchronization ==========

    /** Creates a new MTLEvent (lightweight GPU signal). */
    public static native long mtlDeviceNewEvent(long device);

    /** Creates a new MTLSharedEvent (CPU+GPU signal with value). */
    public static native long mtlDeviceNewSharedEvent(long device);

    /** Gets the signaled value of a shared event. */
    public static native long mtlSharedEventGetSignaledValue(long event);

    /** Sets the signaled value of a shared event from CPU. */
    public static native void mtlSharedEventSetSignaledValue(long event, long value);

    /** Encodes a signal event on a command buffer. */
    public static native void mtlCommandBufferEncodeSignalEvent(long cmdBuffer, long event, long value);

    /** Encodes a wait for event on a command buffer. */
    public static native void mtlCommandBufferEncodeWaitForEvent(long cmdBuffer, long event, long value);

    // ========== Shader Compilation ==========

    /**
     * Compiles MSL source code into a MTLLibrary.
     * @param device device handle
     * @param source MSL source code string
     * @return library handle or 0 on compilation failure
     */
    public static native long mtlDeviceNewLibraryWithSource(long device, String source);

    /** Gets the compilation error/warning message from the last library compilation. */
    public static native String mtlGetLastCompileError();

    /**
     * Creates a MTLFunction from a library.
     * @param library library handle
     * @param name function name
     * @return function handle or 0 if not found
     */
    public static native long mtlLibraryNewFunction(long library, String name);

    // ========== Compute Pipeline ==========

    /** Creates a compute pipeline state from a function. */
    public static native long mtlDeviceNewComputePipelineState(long device, long function);

    // ========== Memory Utility ==========

    /** Fills a memory region with zeros (uses memset on the native side). */
    public static native void memsetZero(long addr, long size);

    /** Fills a memory region with a 32-bit repeated value. */
    public static native void memsetInt(long addr, int value, long count);

    // ========== Metal Constants ==========

    // Storage Modes
    public static final int MTLStorageModeShared  = 0;
    public static final int MTLStorageModeManaged = 1;
    public static final int MTLStorageModePrivate = 2;

    // Resource Options (storage mode is bits 0-3)
    public static final int MTLResourceStorageModeShared  = MTLStorageModeShared << 4;
    public static final int MTLResourceStorageModeManaged = MTLStorageModeManaged << 4;
    public static final int MTLResourceStorageModePrivate = MTLStorageModePrivate << 4;
    public static final int MTLResourceHazardTrackingModeUntracked = 0x1 << 8;

    // CPU Cache Modes
    public static final int MTLResourceCPUCacheModeDefaultCache  = 0;
    public static final int MTLResourceCPUCacheModeWriteCombined = 0x1 << 4;

    // Texture Types
    public static final int MTLTextureType2D = 2;
    public static final int MTLTextureType3D = 4;
    public static final int MTLTextureTypeCube = 5;

    // Texture Usage
    public static final int MTLTextureUsageShaderRead  = 0x0001;
    public static final int MTLTextureUsageShaderWrite = 0x0002;
    public static final int MTLTextureUsageRenderTarget = 0x0004;

    // Load/Store Actions
    public static final int MTLLoadActionDontCare = 0;
    public static final int MTLLoadActionLoad     = 1;
    public static final int MTLLoadActionClear    = 2;
    public static final int MTLStoreActionDontCare = 0;
    public static final int MTLStoreActionStore    = 1;

    // Command Buffer Status
    public static final int MTLCommandBufferStatusCompleted = 4;
    public static final int MTLCommandBufferStatusError     = 5;

    // GPU Family (for capability checking)
    public static final int MTLGPUFamilyApple7 = 1007;
    public static final int MTLGPUFamilyApple8 = 1008;
    public static final int MTLGPUFamilyApple9 = 1009;
}
