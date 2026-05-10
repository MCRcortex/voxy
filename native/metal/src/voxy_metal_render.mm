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
