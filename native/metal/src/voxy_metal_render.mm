// voxy_metal_render.mm — render command encoder JNI methods.
//
// First slice (M3) covers what's needed for a clear-color render pass:
//   * mtlRenderPassSetColorClearColor — fills the att.clearColor field
//   * mtlCommandBufferNewRenderEncoder — produces an MTLRenderCommandEncoder
//     from a MTLRenderPassDescriptor; load actions (including Clear) run here.
// The encoder family for binding pipelines/buffers/textures and issuing draws
// will land in the M5 slice; for M3 we only need to open and immediately close
// an encoder so the load-action clear actually executes.

#include "voxy_metal.h"

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderPassSetColorClearColor(
        JNIEnv *, jclass, jlong descHandle, jint index,
        jfloat r, jfloat g, jfloat b, jfloat a) {
    if (descHandle == 0) return;
    MTLRenderPassDescriptor *desc = voxy_handle_cast<MTLRenderPassDescriptor *>(descHandle);
    MTLRenderPassColorAttachmentDescriptor *att = desc.colorAttachments[(NSUInteger)index];
    att.clearColor = MTLClearColorMake((double)r, (double)g, (double)b, (double)a);
}

extern "C" JNIEXPORT jlong JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlCommandBufferNewRenderEncoder(
        JNIEnv *, jclass, jlong cmdBufferHandle, jlong renderPassDescHandle) {
    if (cmdBufferHandle == 0 || renderPassDescHandle == 0) return 0;
    id<MTLCommandBuffer> cmdBuffer = voxy_handle_cast<id<MTLCommandBuffer>>(cmdBufferHandle);
    MTLRenderPassDescriptor *desc = voxy_handle_cast<MTLRenderPassDescriptor *>(renderPassDescHandle);
    id<MTLRenderCommandEncoder> encoder = [cmdBuffer renderCommandEncoderWithDescriptor:desc];
    if (encoder == nil) return 0;
    return voxy_handle_from(encoder);
}

// -------- Render pipeline descriptor + state (M5) --------

extern "C" JNIEXPORT jlong JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlNewRenderPipelineDescriptor(
        JNIEnv *, jclass) {
    MTLRenderPipelineDescriptor *desc = [[MTLRenderPipelineDescriptor alloc] init];
    if (desc == nil) return 0;
    return voxy_handle_from(desc);
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderPipelineDescriptorSetVertexFunction(
        JNIEnv *, jclass, jlong descHandle, jlong functionHandle) {
    if (descHandle == 0) return;
    MTLRenderPipelineDescriptor *desc = voxy_handle_cast<MTLRenderPipelineDescriptor *>(descHandle);
    desc.vertexFunction = functionHandle ? voxy_handle_cast<id<MTLFunction>>(functionHandle) : nil;
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderPipelineDescriptorSetFragmentFunction(
        JNIEnv *, jclass, jlong descHandle, jlong functionHandle) {
    if (descHandle == 0) return;
    MTLRenderPipelineDescriptor *desc = voxy_handle_cast<MTLRenderPipelineDescriptor *>(descHandle);
    desc.fragmentFunction = functionHandle ? voxy_handle_cast<id<MTLFunction>>(functionHandle) : nil;
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderPipelineDescriptorSetColorAttachmentFormat(
        JNIEnv *, jclass, jlong descHandle, jint index, jint pixelFormat) {
    if (descHandle == 0) return;
    MTLRenderPipelineDescriptor *desc = voxy_handle_cast<MTLRenderPipelineDescriptor *>(descHandle);
    desc.colorAttachments[(NSUInteger)index].pixelFormat = (MTLPixelFormat)pixelFormat;
}

extern "C" JNIEXPORT jlong JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlDeviceNewRenderPipelineState(
        JNIEnv *, jclass, jlong deviceHandle, jlong descHandle) {
    if (deviceHandle == 0 || descHandle == 0) return 0;
    id<MTLDevice> device = voxy_handle_cast<id<MTLDevice>>(deviceHandle);
    MTLRenderPipelineDescriptor *desc = voxy_handle_cast<MTLRenderPipelineDescriptor *>(descHandle);
    NSError *err = nil;
    id<MTLRenderPipelineState> state = [device newRenderPipelineStateWithDescriptor:desc error:&err];
    if (state == nil) {
        voxy_set_last_error(err ? err.localizedDescription : @"newRenderPipelineState returned nil");
        return 0;
    }
    return voxy_handle_from(state);
}

// -------- Render encoder draw operations (M5) --------

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderEncoderSetRenderPipelineState(
        JNIEnv *, jclass, jlong encoderHandle, jlong pipelineStateHandle) {
    if (encoderHandle == 0 || pipelineStateHandle == 0) return;
    id<MTLRenderCommandEncoder> encoder = voxy_handle_cast<id<MTLRenderCommandEncoder>>(encoderHandle);
    id<MTLRenderPipelineState> state = voxy_handle_cast<id<MTLRenderPipelineState>>(pipelineStateHandle);
    [encoder setRenderPipelineState:state];
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderEncoderDrawPrimitives(
        JNIEnv *, jclass, jlong encoderHandle,
        jint primitiveType, jint firstVertex, jint vertexCount,
        jint instanceCount, jint baseInstance) {
    if (encoderHandle == 0) return;
    id<MTLRenderCommandEncoder> encoder = voxy_handle_cast<id<MTLRenderCommandEncoder>>(encoderHandle);
    [encoder drawPrimitives:(MTLPrimitiveType)primitiveType
                vertexStart:(NSUInteger)firstVertex
                vertexCount:(NSUInteger)vertexCount
              instanceCount:(NSUInteger)instanceCount
               baseInstance:(NSUInteger)baseInstance];
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderEncoderDrawIndexedPrimitives(
        JNIEnv *, jclass, jlong encoderHandle,
        jint primitiveType, jint indexCount, jint indexType,
        jlong indexBufferHandle, jlong indexBufferOffset,
        jint instanceCount, jint baseVertex, jint baseInstance) {
    if (encoderHandle == 0 || indexBufferHandle == 0) return;
    id<MTLRenderCommandEncoder> encoder = voxy_handle_cast<id<MTLRenderCommandEncoder>>(encoderHandle);
    id<MTLBuffer> indexBuffer = voxy_handle_cast<id<MTLBuffer>>(indexBufferHandle);
    [encoder drawIndexedPrimitives:(MTLPrimitiveType)primitiveType
                        indexCount:(NSUInteger)indexCount
                         indexType:(MTLIndexType)indexType
                       indexBuffer:indexBuffer
                 indexBufferOffset:(NSUInteger)indexBufferOffset
                     instanceCount:(NSUInteger)instanceCount
                        baseVertex:(NSInteger)baseVertex
                      baseInstance:(NSUInteger)baseInstance];
}

// -------- Per-stage resource binding --------

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderEncoderSetVertexBuffer(
        JNIEnv *, jclass, jlong encoderHandle, jlong bufferHandle, jlong offset, jint index) {
    if (encoderHandle == 0) return;
    id<MTLRenderCommandEncoder> encoder = voxy_handle_cast<id<MTLRenderCommandEncoder>>(encoderHandle);
    id<MTLBuffer> buffer = bufferHandle ? voxy_handle_cast<id<MTLBuffer>>(bufferHandle) : nil;
    [encoder setVertexBuffer:buffer offset:(NSUInteger)offset atIndex:(NSUInteger)index];
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderEncoderSetFragmentBuffer(
        JNIEnv *, jclass, jlong encoderHandle, jlong bufferHandle, jlong offset, jint index) {
    if (encoderHandle == 0) return;
    id<MTLRenderCommandEncoder> encoder = voxy_handle_cast<id<MTLRenderCommandEncoder>>(encoderHandle);
    id<MTLBuffer> buffer = bufferHandle ? voxy_handle_cast<id<MTLBuffer>>(bufferHandle) : nil;
    [encoder setFragmentBuffer:buffer offset:(NSUInteger)offset atIndex:(NSUInteger)index];
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderEncoderSetVertexTexture(
        JNIEnv *, jclass, jlong encoderHandle, jlong textureHandle, jint index) {
    if (encoderHandle == 0) return;
    id<MTLRenderCommandEncoder> encoder = voxy_handle_cast<id<MTLRenderCommandEncoder>>(encoderHandle);
    id<MTLTexture> texture = textureHandle ? voxy_handle_cast<id<MTLTexture>>(textureHandle) : nil;
    [encoder setVertexTexture:texture atIndex:(NSUInteger)index];
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderEncoderSetFragmentTexture(
        JNIEnv *, jclass, jlong encoderHandle, jlong textureHandle, jint index) {
    if (encoderHandle == 0) return;
    id<MTLRenderCommandEncoder> encoder = voxy_handle_cast<id<MTLRenderCommandEncoder>>(encoderHandle);
    id<MTLTexture> texture = textureHandle ? voxy_handle_cast<id<MTLTexture>>(textureHandle) : nil;
    [encoder setFragmentTexture:texture atIndex:(NSUInteger)index];
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderEncoderSetViewport(
        JNIEnv *, jclass, jlong encoderHandle,
        jdouble originX, jdouble originY, jdouble width, jdouble height,
        jdouble znear, jdouble zfar) {
    if (encoderHandle == 0) return;
    id<MTLRenderCommandEncoder> encoder = voxy_handle_cast<id<MTLRenderCommandEncoder>>(encoderHandle);
    MTLViewport viewport = {originX, originY, width, height, znear, zfar};
    [encoder setViewport:viewport];
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderEncoderSetScissorRect(
        JNIEnv *, jclass, jlong encoderHandle, jint x, jint y, jint width, jint height) {
    if (encoderHandle == 0) return;
    id<MTLRenderCommandEncoder> encoder = voxy_handle_cast<id<MTLRenderCommandEncoder>>(encoderHandle);
    MTLScissorRect rect = {(NSUInteger)x, (NSUInteger)y, (NSUInteger)width, (NSUInteger)height};
    [encoder setScissorRect:rect];
}

// -------- Vertex descriptor --------

extern "C" JNIEXPORT jlong JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlNewVertexDescriptor(
        JNIEnv *, jclass) {
    MTLVertexDescriptor *desc = [[MTLVertexDescriptor alloc] init];
    if (desc == nil) return 0;
    return voxy_handle_from(desc);
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlVertexDescriptorSetAttribute(
        JNIEnv *, jclass, jlong descHandle,
        jint index, jint format, jlong offset, jint bufferIndex) {
    if (descHandle == 0) return;
    MTLVertexDescriptor *desc = voxy_handle_cast<MTLVertexDescriptor *>(descHandle);
    MTLVertexAttributeDescriptor *attr = desc.attributes[(NSUInteger)index];
    attr.format = (MTLVertexFormat)format;
    attr.offset = (NSUInteger)offset;
    attr.bufferIndex = (NSUInteger)bufferIndex;
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlVertexDescriptorSetLayout(
        JNIEnv *, jclass, jlong descHandle,
        jint bufferIndex, jlong stride, jint stepFunction, jint stepRate) {
    if (descHandle == 0) return;
    MTLVertexDescriptor *desc = voxy_handle_cast<MTLVertexDescriptor *>(descHandle);
    MTLVertexBufferLayoutDescriptor *layout = desc.layouts[(NSUInteger)bufferIndex];
    layout.stride = (NSUInteger)stride;
    layout.stepFunction = (MTLVertexStepFunction)stepFunction;
    layout.stepRate = (NSUInteger)stepRate;
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderPipelineDescriptorSetVertexDescriptor(
        JNIEnv *, jclass, jlong pipelineDescHandle, jlong vertexDescHandle) {
    if (pipelineDescHandle == 0) return;
    MTLRenderPipelineDescriptor *pipelineDesc = voxy_handle_cast<MTLRenderPipelineDescriptor *>(pipelineDescHandle);
    MTLVertexDescriptor *vertexDesc = vertexDescHandle ? voxy_handle_cast<MTLVertexDescriptor *>(vertexDescHandle) : nil;
    pipelineDesc.vertexDescriptor = vertexDesc;
}

// -------- Indirect draw (single per call) --------

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderEncoderDrawPrimitivesIndirect(
        JNIEnv *, jclass, jlong encoderHandle, jint primitiveType,
        jlong indirectBufferHandle, jlong indirectOffset) {
    if (encoderHandle == 0 || indirectBufferHandle == 0) return;
    id<MTLRenderCommandEncoder> encoder = voxy_handle_cast<id<MTLRenderCommandEncoder>>(encoderHandle);
    id<MTLBuffer> indirectBuffer = voxy_handle_cast<id<MTLBuffer>>(indirectBufferHandle);
    [encoder drawPrimitives:(MTLPrimitiveType)primitiveType
             indirectBuffer:indirectBuffer
       indirectBufferOffset:(NSUInteger)indirectOffset];
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderEncoderDrawIndexedPrimitivesIndirect(
        JNIEnv *, jclass, jlong encoderHandle, jint primitiveType, jint indexType,
        jlong indexBufferHandle, jlong indexBufferOffset,
        jlong indirectBufferHandle, jlong indirectOffset) {
    if (encoderHandle == 0 || indexBufferHandle == 0 || indirectBufferHandle == 0) return;
    id<MTLRenderCommandEncoder> encoder = voxy_handle_cast<id<MTLRenderCommandEncoder>>(encoderHandle);
    id<MTLBuffer> indexBuffer = voxy_handle_cast<id<MTLBuffer>>(indexBufferHandle);
    id<MTLBuffer> indirectBuffer = voxy_handle_cast<id<MTLBuffer>>(indirectBufferHandle);
    [encoder drawIndexedPrimitives:(MTLPrimitiveType)primitiveType
                         indexType:(MTLIndexType)indexType
                       indexBuffer:indexBuffer
                 indexBufferOffset:(NSUInteger)indexBufferOffset
                    indirectBuffer:indirectBuffer
              indirectBufferOffset:(NSUInteger)indirectOffset];
}

// -------- Blit encoder readback (M5) --------

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlBlitEncoderCopyTextureToBuffer(
        JNIEnv *, jclass, jlong encoderHandle,
        jlong srcTextureHandle, jint srcLevel,
        jint srcX, jint srcY, jint srcWidth, jint srcHeight,
        jlong dstBufferHandle, jlong dstOffset, jint bytesPerRow, jint bytesPerImage) {
    if (encoderHandle == 0 || srcTextureHandle == 0 || dstBufferHandle == 0) return;
    id<MTLBlitCommandEncoder> encoder = voxy_handle_cast<id<MTLBlitCommandEncoder>>(encoderHandle);
    id<MTLTexture> srcTexture = voxy_handle_cast<id<MTLTexture>>(srcTextureHandle);
    id<MTLBuffer> dstBuffer = voxy_handle_cast<id<MTLBuffer>>(dstBufferHandle);
    MTLOrigin origin = MTLOriginMake((NSUInteger)srcX, (NSUInteger)srcY, 0);
    MTLSize size = MTLSizeMake((NSUInteger)srcWidth, (NSUInteger)srcHeight, 1);
    [encoder copyFromTexture:srcTexture
                 sourceSlice:0
                 sourceLevel:(NSUInteger)srcLevel
                sourceOrigin:origin
                  sourceSize:size
                    toBuffer:dstBuffer
           destinationOffset:(NSUInteger)dstOffset
      destinationBytesPerRow:(NSUInteger)bytesPerRow
    destinationBytesPerImage:(NSUInteger)bytesPerImage];
}

// -------- Blend state on render pipeline descriptor --------

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderPipelineDescriptorSetColorAttachmentBlending(
        JNIEnv *, jclass, jlong descHandle, jint index, jboolean enable,
        jint rgbOp, jint alphaOp,
        jint srcRgb, jint dstRgb, jint srcAlpha, jint dstAlpha) {
    if (descHandle == 0) return;
    MTLRenderPipelineDescriptor *desc = voxy_handle_cast<MTLRenderPipelineDescriptor *>(descHandle);
    MTLRenderPipelineColorAttachmentDescriptor *att = desc.colorAttachments[(NSUInteger)index];
    att.blendingEnabled = (enable == JNI_TRUE);
    att.rgbBlendOperation = (MTLBlendOperation)rgbOp;
    att.alphaBlendOperation = (MTLBlendOperation)alphaOp;
    att.sourceRGBBlendFactor = (MTLBlendFactor)srcRgb;
    att.destinationRGBBlendFactor = (MTLBlendFactor)dstRgb;
    att.sourceAlphaBlendFactor = (MTLBlendFactor)srcAlpha;
    att.destinationAlphaBlendFactor = (MTLBlendFactor)dstAlpha;
}

// -------- Depth-stencil descriptor + state --------

extern "C" JNIEXPORT jlong JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlNewDepthStencilDescriptor(JNIEnv *, jclass) {
    MTLDepthStencilDescriptor *desc = [[MTLDepthStencilDescriptor alloc] init];
    if (desc == nil) return 0;
    return voxy_handle_from(desc);
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlDepthStencilDescriptorSetCompareFunction(
        JNIEnv *, jclass, jlong descHandle, jint compareFunction) {
    if (descHandle == 0) return;
    MTLDepthStencilDescriptor *desc = voxy_handle_cast<MTLDepthStencilDescriptor *>(descHandle);
    desc.depthCompareFunction = (MTLCompareFunction)compareFunction;
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlDepthStencilDescriptorSetDepthWriteEnabled(
        JNIEnv *, jclass, jlong descHandle, jboolean enabled) {
    if (descHandle == 0) return;
    MTLDepthStencilDescriptor *desc = voxy_handle_cast<MTLDepthStencilDescriptor *>(descHandle);
    desc.depthWriteEnabled = (enabled == JNI_TRUE);
}

extern "C" JNIEXPORT jlong JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlDeviceNewDepthStencilState(
        JNIEnv *, jclass, jlong deviceHandle, jlong descHandle) {
    if (deviceHandle == 0 || descHandle == 0) return 0;
    id<MTLDevice> device = voxy_handle_cast<id<MTLDevice>>(deviceHandle);
    MTLDepthStencilDescriptor *desc = voxy_handle_cast<MTLDepthStencilDescriptor *>(descHandle);
    id<MTLDepthStencilState> state = [device newDepthStencilStateWithDescriptor:desc];
    if (state == nil) return 0;
    return voxy_handle_from(state);
}

// -------- Render encoder static state --------

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderEncoderSetDepthStencilState(
        JNIEnv *, jclass, jlong encoderHandle, jlong stateHandle) {
    if (encoderHandle == 0) return;
    id<MTLRenderCommandEncoder> encoder = voxy_handle_cast<id<MTLRenderCommandEncoder>>(encoderHandle);
    id<MTLDepthStencilState> state = stateHandle ? voxy_handle_cast<id<MTLDepthStencilState>>(stateHandle) : nil;
    [encoder setDepthStencilState:state];
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderEncoderSetCullMode(
        JNIEnv *, jclass, jlong encoderHandle, jint cullMode) {
    if (encoderHandle == 0) return;
    id<MTLRenderCommandEncoder> encoder = voxy_handle_cast<id<MTLRenderCommandEncoder>>(encoderHandle);
    [encoder setCullMode:(MTLCullMode)cullMode];
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderEncoderSetFrontFacingWinding(
        JNIEnv *, jclass, jlong encoderHandle, jint winding) {
    if (encoderHandle == 0) return;
    id<MTLRenderCommandEncoder> encoder = voxy_handle_cast<id<MTLRenderCommandEncoder>>(encoderHandle);
    [encoder setFrontFacingWinding:(MTLWinding)winding];
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderEncoderSetTriangleFillMode(
        JNIEnv *, jclass, jlong encoderHandle, jint fillMode) {
    if (encoderHandle == 0) return;
    id<MTLRenderCommandEncoder> encoder = voxy_handle_cast<id<MTLRenderCommandEncoder>>(encoderHandle);
    [encoder setTriangleFillMode:(MTLTriangleFillMode)fillMode];
}

// -------- Sampler descriptor + state --------

extern "C" JNIEXPORT jlong JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlNewSamplerDescriptor(JNIEnv *, jclass) {
    MTLSamplerDescriptor *desc = [[MTLSamplerDescriptor alloc] init];
    if (desc == nil) return 0;
    return voxy_handle_from(desc);
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlSamplerDescriptorSetMinFilter(
        JNIEnv *, jclass, jlong descHandle, jint filter) {
    if (descHandle == 0) return;
    MTLSamplerDescriptor *desc = voxy_handle_cast<MTLSamplerDescriptor *>(descHandle);
    desc.minFilter = (MTLSamplerMinMagFilter)filter;
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlSamplerDescriptorSetMagFilter(
        JNIEnv *, jclass, jlong descHandle, jint filter) {
    if (descHandle == 0) return;
    MTLSamplerDescriptor *desc = voxy_handle_cast<MTLSamplerDescriptor *>(descHandle);
    desc.magFilter = (MTLSamplerMinMagFilter)filter;
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlSamplerDescriptorSetMipFilter(
        JNIEnv *, jclass, jlong descHandle, jint filter) {
    if (descHandle == 0) return;
    MTLSamplerDescriptor *desc = voxy_handle_cast<MTLSamplerDescriptor *>(descHandle);
    desc.mipFilter = (MTLSamplerMipFilter)filter;
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlSamplerDescriptorSetSAddressMode(
        JNIEnv *, jclass, jlong descHandle, jint mode) {
    if (descHandle == 0) return;
    MTLSamplerDescriptor *desc = voxy_handle_cast<MTLSamplerDescriptor *>(descHandle);
    desc.sAddressMode = (MTLSamplerAddressMode)mode;
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlSamplerDescriptorSetTAddressMode(
        JNIEnv *, jclass, jlong descHandle, jint mode) {
    if (descHandle == 0) return;
    MTLSamplerDescriptor *desc = voxy_handle_cast<MTLSamplerDescriptor *>(descHandle);
    desc.tAddressMode = (MTLSamplerAddressMode)mode;
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlSamplerDescriptorSetRAddressMode(
        JNIEnv *, jclass, jlong descHandle, jint mode) {
    if (descHandle == 0) return;
    MTLSamplerDescriptor *desc = voxy_handle_cast<MTLSamplerDescriptor *>(descHandle);
    desc.rAddressMode = (MTLSamplerAddressMode)mode;
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlSamplerDescriptorSetLodMinClamp(
        JNIEnv *, jclass, jlong descHandle, jfloat value) {
    if (descHandle == 0) return;
    MTLSamplerDescriptor *desc = voxy_handle_cast<MTLSamplerDescriptor *>(descHandle);
    desc.lodMinClamp = value;
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlSamplerDescriptorSetLodMaxClamp(
        JNIEnv *, jclass, jlong descHandle, jfloat value) {
    if (descHandle == 0) return;
    MTLSamplerDescriptor *desc = voxy_handle_cast<MTLSamplerDescriptor *>(descHandle);
    desc.lodMaxClamp = value;
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlSamplerDescriptorSetCompareFunction(
        JNIEnv *, jclass, jlong descHandle, jint compareFunction) {
    if (descHandle == 0) return;
    MTLSamplerDescriptor *desc = voxy_handle_cast<MTLSamplerDescriptor *>(descHandle);
    desc.compareFunction = (MTLCompareFunction)compareFunction;
}

extern "C" JNIEXPORT jlong JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlDeviceNewSamplerState(
        JNIEnv *, jclass, jlong deviceHandle, jlong descHandle) {
    if (deviceHandle == 0 || descHandle == 0) return 0;
    id<MTLDevice> device = voxy_handle_cast<id<MTLDevice>>(deviceHandle);
    MTLSamplerDescriptor *desc = voxy_handle_cast<MTLSamplerDescriptor *>(descHandle);
    id<MTLSamplerState> state = [device newSamplerStateWithDescriptor:desc];
    if (state == nil) return 0;
    return voxy_handle_from(state);
}

// -------- Sampler binding on render encoder --------

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderEncoderSetVertexSamplerState(
        JNIEnv *, jclass, jlong encoderHandle, jlong samplerHandle, jint index) {
    if (encoderHandle == 0) return;
    id<MTLRenderCommandEncoder> encoder = voxy_handle_cast<id<MTLRenderCommandEncoder>>(encoderHandle);
    id<MTLSamplerState> sampler = samplerHandle ? voxy_handle_cast<id<MTLSamplerState>>(samplerHandle) : nil;
    [encoder setVertexSamplerState:sampler atIndex:(NSUInteger)index];
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderEncoderSetFragmentSamplerState(
        JNIEnv *, jclass, jlong encoderHandle, jlong samplerHandle, jint index) {
    if (encoderHandle == 0) return;
    id<MTLRenderCommandEncoder> encoder = voxy_handle_cast<id<MTLRenderCommandEncoder>>(encoderHandle);
    id<MTLSamplerState> sampler = samplerHandle ? voxy_handle_cast<id<MTLSamplerState>>(samplerHandle) : nil;
    [encoder setFragmentSamplerState:sampler atIndex:(NSUInteger)index];
}

// -------- Inline byte uniforms (render encoder) --------

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderEncoderSetVertexBytes(
        JNIEnv *, jclass, jlong encoderHandle, jlong dataAddr, jint size, jint index) {
    if (encoderHandle == 0 || dataAddr == 0) return;
    id<MTLRenderCommandEncoder> encoder = voxy_handle_cast<id<MTLRenderCommandEncoder>>(encoderHandle);
    [encoder setVertexBytes:(const void *)dataAddr length:(NSUInteger)size atIndex:(NSUInteger)index];
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderEncoderSetFragmentBytes(
        JNIEnv *, jclass, jlong encoderHandle, jlong dataAddr, jint size, jint index) {
    if (encoderHandle == 0 || dataAddr == 0) return;
    id<MTLRenderCommandEncoder> encoder = voxy_handle_cast<id<MTLRenderCommandEncoder>>(encoderHandle);
    [encoder setFragmentBytes:(const void *)dataAddr length:(NSUInteger)size atIndex:(NSUInteger)index];
}
