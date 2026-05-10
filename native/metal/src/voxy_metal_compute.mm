// voxy_metal_compute.mm — MTLComputeCommandEncoder JNI methods.
//
// Covers M7: open a compute encoder on a command buffer, bind a compute
// pipeline state, bind buffers, and dispatch thread-groups. Texture binding
// + indirect dispatch + memory-barrier-within-pass land in M8/M9 alongside
// the HiZBuffer2 and traversal_dev migrations.

#include "voxy_metal.h"

extern "C" JNIEXPORT jlong JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlCommandBufferNewComputeEncoder(
        JNIEnv *, jclass, jlong cmdBufferHandle) {
    if (cmdBufferHandle == 0) return 0;
    id<MTLCommandBuffer> cmdBuffer = voxy_handle_cast<id<MTLCommandBuffer>>(cmdBufferHandle);
    id<MTLComputeCommandEncoder> encoder = [cmdBuffer computeCommandEncoder];
    if (encoder == nil) return 0;
    return voxy_handle_from(encoder);
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlComputeEncoderSetComputePipelineState(
        JNIEnv *, jclass, jlong encoderHandle, jlong pipelineStateHandle) {
    if (encoderHandle == 0 || pipelineStateHandle == 0) return;
    id<MTLComputeCommandEncoder> encoder = voxy_handle_cast<id<MTLComputeCommandEncoder>>(encoderHandle);
    id<MTLComputePipelineState> state = voxy_handle_cast<id<MTLComputePipelineState>>(pipelineStateHandle);
    [encoder setComputePipelineState:state];
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlComputeEncoderSetBuffer(
        JNIEnv *, jclass, jlong encoderHandle, jlong bufferHandle, jlong offset, jint index) {
    if (encoderHandle == 0) return;
    id<MTLComputeCommandEncoder> encoder = voxy_handle_cast<id<MTLComputeCommandEncoder>>(encoderHandle);
    id<MTLBuffer> buffer = bufferHandle ? voxy_handle_cast<id<MTLBuffer>>(bufferHandle) : nil;
    [encoder setBuffer:buffer offset:(NSUInteger)offset atIndex:(NSUInteger)index];
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlComputeEncoderDispatchThreadgroups(
        JNIEnv *, jclass, jlong encoderHandle,
        jint gx, jint gy, jint gz, jint tx, jint ty, jint tz) {
    if (encoderHandle == 0) return;
    id<MTLComputeCommandEncoder> encoder = voxy_handle_cast<id<MTLComputeCommandEncoder>>(encoderHandle);
    MTLSize threadgroups = MTLSizeMake((NSUInteger)gx, (NSUInteger)gy, (NSUInteger)gz);
    MTLSize threadsPerThreadgroup = MTLSizeMake((NSUInteger)tx, (NSUInteger)ty, (NSUInteger)tz);
    [encoder dispatchThreadgroups:threadgroups threadsPerThreadgroup:threadsPerThreadgroup];
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlComputeEncoderSetTexture(
        JNIEnv *, jclass, jlong encoderHandle, jlong textureHandle, jint index) {
    if (encoderHandle == 0) return;
    id<MTLComputeCommandEncoder> encoder = voxy_handle_cast<id<MTLComputeCommandEncoder>>(encoderHandle);
    id<MTLTexture> texture = textureHandle ? voxy_handle_cast<id<MTLTexture>>(textureHandle) : nil;
    [encoder setTexture:texture atIndex:(NSUInteger)index];
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlComputeEncoderDispatchThreadgroupsIndirect(
        JNIEnv *, jclass, jlong encoderHandle,
        jlong indirectBufferHandle, jlong indirectOffset, jint tx, jint ty, jint tz) {
    if (encoderHandle == 0 || indirectBufferHandle == 0) return;
    id<MTLComputeCommandEncoder> encoder = voxy_handle_cast<id<MTLComputeCommandEncoder>>(encoderHandle);
    id<MTLBuffer> indirectBuffer = voxy_handle_cast<id<MTLBuffer>>(indirectBufferHandle);
    MTLSize threadsPerThreadgroup = MTLSizeMake((NSUInteger)tx, (NSUInteger)ty, (NSUInteger)tz);
    [encoder dispatchThreadgroupsWithIndirectBuffer:indirectBuffer
                               indirectBufferOffset:(NSUInteger)indirectOffset
                              threadsPerThreadgroup:threadsPerThreadgroup];
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlComputeEncoderMemoryBarrier(
        JNIEnv *, jclass, jlong encoderHandle, jint scope) {
    if (encoderHandle == 0) return;
    id<MTLComputeCommandEncoder> encoder = voxy_handle_cast<id<MTLComputeCommandEncoder>>(encoderHandle);
    [encoder memoryBarrierWithScope:(MTLBarrierScope)scope];
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlComputeEncoderSetSamplerState(
        JNIEnv *, jclass, jlong encoderHandle, jlong samplerHandle, jint index) {
    if (encoderHandle == 0) return;
    id<MTLComputeCommandEncoder> encoder = voxy_handle_cast<id<MTLComputeCommandEncoder>>(encoderHandle);
    id<MTLSamplerState> sampler = samplerHandle ? voxy_handle_cast<id<MTLSamplerState>>(samplerHandle) : nil;
    [encoder setSamplerState:sampler atIndex:(NSUInteger)index];
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlComputeEncoderSetBytes(
        JNIEnv *, jclass, jlong encoderHandle, jlong dataAddr, jint size, jint index) {
    if (encoderHandle == 0 || dataAddr == 0) return;
    id<MTLComputeCommandEncoder> encoder = voxy_handle_cast<id<MTLComputeCommandEncoder>>(encoderHandle);
    [encoder setBytes:(const void *)dataAddr length:(NSUInteger)size atIndex:(NSUInteger)index];
}
