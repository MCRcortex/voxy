// voxy_metal_icb.mm — MTLIndirectCommandBuffer JNI methods (Blocker 1).
//
// MDIC's central render path on macOS needs the ability to issue
// glMultiDrawElementsIndirectCountARB-equivalent draws with a GPU-resident
// draw count. Metal has no direct equivalent; the canonical workaround is
// to pre-allocate an MTLIndirectCommandBuffer, populate it via a compute
// prepass (cmdgen.comp's M9 rewrite), then call
// executeCommandsInBuffer:indirectBuffer:indirectBufferOffset: on the
// render encoder so the GPU itself decides which (location, length) slice
// of the ICB actually renders.
//
// Lifetime note — MTLIndirectRenderCommand handles returned from
// `[icb indirectRenderCommandAtIndex:]` are owned by the parent ICB but
// ARC will autorelease them when the local variable goes out of scope on
// the C++ side. To avoid exposing dangling pointers across the JNI
// boundary, every CPU-side population JNI takes (icbHandle, commandIndex)
// and fetches the command inline — the handle never leaves Objective-C++.
//
// The Java side lives in MetalIndirectCommandBuffer + the new methods on
// MetalRenderBackend / MetalRenderEncoder.

#include "voxy_metal.h"

// ---------- Pipeline-descriptor support for ICB use ----------

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderPipelineDescriptorSetSupportIndirectCommandBuffers(
        JNIEnv *, jclass, jlong descHandle, jboolean enabled) {
    if (descHandle == 0) return;
    MTLRenderPipelineDescriptor *desc = voxy_handle_cast<MTLRenderPipelineDescriptor *>(descHandle);
    desc.supportIndirectCommandBuffers = enabled == JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderPipelineDescriptorSetDepthAttachmentPixelFormat(
        JNIEnv *, jclass, jlong descHandle, jint pixelFormat) {
    if (descHandle == 0) return;
    MTLRenderPipelineDescriptor *desc = voxy_handle_cast<MTLRenderPipelineDescriptor *>(descHandle);
    desc.depthAttachmentPixelFormat = (MTLPixelFormat)pixelFormat;
}

// ---------- ICB creation ----------

extern "C" JNIEXPORT jlong JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlDeviceNewIndirectCommandBuffer(
        JNIEnv *, jclass, jlong deviceHandle,
        jint commandTypes, jint maxCommandCount, jint options) {
    if (deviceHandle == 0) return 0;
    id<MTLDevice> device = voxy_handle_cast<id<MTLDevice>>(deviceHandle);

    MTLIndirectCommandBufferDescriptor *desc = [[MTLIndirectCommandBufferDescriptor alloc] init];
    desc.commandTypes = (MTLIndirectCommandType)commandTypes;
    desc.inheritPipelineState = (options & 0x1) != 0;
    desc.inheritBuffers       = (options & 0x2) != 0;
    desc.maxVertexBufferBindCount   = 31;
    desc.maxFragmentBufferBindCount = 31;

    id<MTLIndirectCommandBuffer> icb = [device newIndirectCommandBufferWithDescriptor:desc
                                                                      maxCommandCount:(NSUInteger)maxCommandCount
                                                                              options:0];
    if (icb == nil) return 0;
    return voxy_handle_from(icb);
}

// ---------- ICB reset ----------

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlIndirectCommandBufferReset(
        JNIEnv *, jclass, jlong icbHandle, jint rangeStart, jint rangeLength) {
    if (icbHandle == 0 || rangeLength <= 0) return;
    id<MTLIndirectCommandBuffer> icb = voxy_handle_cast<id<MTLIndirectCommandBuffer>>(icbHandle);
    NSRange range = NSMakeRange((NSUInteger)rangeStart, (NSUInteger)rangeLength);
    [icb resetWithRange:range];
}

// ---------- Stub: command-at lookup ----------
// Retained for API completeness, but the value should NOT be passed back to
// JNI methods — fetch+populate happen inline below.

extern "C" JNIEXPORT jlong JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlIndirectCommandBufferGetCommand(
        JNIEnv *, jclass, jlong icbHandle, jint commandIndex) {
    if (icbHandle == 0) return 0;
    id<MTLIndirectCommandBuffer> icb = voxy_handle_cast<id<MTLIndirectCommandBuffer>>(icbHandle);
    // Touch the slot so any ICB-level lazy init runs; return a non-zero stub
    // so the Java side can sanity-check the index without dereferencing.
    id<MTLIndirectRenderCommand> cmd = [icb indirectRenderCommandAtIndex:(NSUInteger)commandIndex];
    return cmd != nil ? (jlong)1 : (jlong)0;
}

// ---------- Per-command population (CPU-side, inline command lookup) ----------

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlIndirectRenderCommandSetPipelineState(
        JNIEnv *, jclass, jlong icbHandle, jint commandIndex, jlong psoHandle) {
    if (icbHandle == 0 || psoHandle == 0) return;
    id<MTLIndirectCommandBuffer> icb = voxy_handle_cast<id<MTLIndirectCommandBuffer>>(icbHandle);
    id<MTLRenderPipelineState> pso = voxy_handle_cast<id<MTLRenderPipelineState>>(psoHandle);
    id<MTLIndirectRenderCommand> cmd = [icb indirectRenderCommandAtIndex:(NSUInteger)commandIndex];
    if ([cmd respondsToSelector:@selector(setRenderPipelineState:)]) {
        [cmd setRenderPipelineState:pso];
    }
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlIndirectRenderCommandSetVertexBuffer(
        JNIEnv *, jclass, jlong icbHandle, jint commandIndex,
        jlong bufferHandle, jlong offset, jint atIndex) {
    if (icbHandle == 0 || bufferHandle == 0) return;
    id<MTLIndirectCommandBuffer> icb = voxy_handle_cast<id<MTLIndirectCommandBuffer>>(icbHandle);
    id<MTLBuffer> buf = voxy_handle_cast<id<MTLBuffer>>(bufferHandle);
    id<MTLIndirectRenderCommand> cmd = [icb indirectRenderCommandAtIndex:(NSUInteger)commandIndex];
    [cmd setVertexBuffer:buf offset:(NSUInteger)offset atIndex:(NSUInteger)atIndex];
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlIndirectRenderCommandSetFragmentBuffer(
        JNIEnv *, jclass, jlong icbHandle, jint commandIndex,
        jlong bufferHandle, jlong offset, jint atIndex) {
    if (icbHandle == 0 || bufferHandle == 0) return;
    id<MTLIndirectCommandBuffer> icb = voxy_handle_cast<id<MTLIndirectCommandBuffer>>(icbHandle);
    id<MTLBuffer> buf = voxy_handle_cast<id<MTLBuffer>>(bufferHandle);
    id<MTLIndirectRenderCommand> cmd = [icb indirectRenderCommandAtIndex:(NSUInteger)commandIndex];
    [cmd setFragmentBuffer:buf offset:(NSUInteger)offset atIndex:(NSUInteger)atIndex];
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlIndirectRenderCommandDrawIndexedPrimitives(
        JNIEnv *, jclass, jlong icbHandle, jint commandIndex,
        jint primitiveType, jint indexCount, jint indexType,
        jlong indexBufferHandle, jlong indexBufferOffset,
        jint instanceCount, jint baseVertex, jint baseInstance) {
    if (icbHandle == 0 || indexBufferHandle == 0) return;
    id<MTLIndirectCommandBuffer> icb = voxy_handle_cast<id<MTLIndirectCommandBuffer>>(icbHandle);
    id<MTLBuffer> indexBuf = voxy_handle_cast<id<MTLBuffer>>(indexBufferHandle);
    id<MTLIndirectRenderCommand> cmd = [icb indirectRenderCommandAtIndex:(NSUInteger)commandIndex];
    [cmd drawIndexedPrimitives:(MTLPrimitiveType)primitiveType
                    indexCount:(NSUInteger)indexCount
                     indexType:(MTLIndexType)indexType
                   indexBuffer:indexBuf
             indexBufferOffset:(NSUInteger)indexBufferOffset
                 instanceCount:(NSUInteger)instanceCount
                    baseVertex:(NSInteger)baseVertex
                  baseInstance:(NSUInteger)baseInstance];
}

// ---------- Encoder-side resource declaration ----------
// executeCommandsInBuffer needs the parent encoder to know that the
// resources the ICB will read are still live. For ICBs created with
// inheritBuffers=YES the inherited bindings are tracked automatically,
// but the indirect index buffer (passed to drawIndexedPrimitives:) is
// NOT inherited — Metal validation aborts with errorDescription
// "Indirect Command Buffer reads from <buf> which has not been declared
// to the encoder" unless we declare it via useResource:.

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderEncoderUseResource(
        JNIEnv *, jclass, jlong encoderHandle, jlong resourceHandle, jint usage, jint stages) {
    if (encoderHandle == 0 || resourceHandle == 0) return;
    id<MTLRenderCommandEncoder> encoder = voxy_handle_cast<id<MTLRenderCommandEncoder>>(encoderHandle);
    id<MTLResource> resource = voxy_handle_cast<id<MTLResource>>(resourceHandle);
    [encoder useResource:resource
                   usage:(MTLResourceUsage)usage
                  stages:(MTLRenderStages)stages];
}

// ---------- ICB execution (render encoder side) ----------

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRenderEncoderExecuteCommandsInBuffer(
        JNIEnv *, jclass, jlong encoderHandle,
        jlong icbHandle, jlong rangeBufferHandle, jlong rangeOffset) {
    if (encoderHandle == 0 || icbHandle == 0 || rangeBufferHandle == 0) return;
    id<MTLRenderCommandEncoder> encoder = voxy_handle_cast<id<MTLRenderCommandEncoder>>(encoderHandle);
    id<MTLIndirectCommandBuffer> icb = voxy_handle_cast<id<MTLIndirectCommandBuffer>>(icbHandle);
    id<MTLBuffer> rangeBuf = voxy_handle_cast<id<MTLBuffer>>(rangeBufferHandle);
    [encoder executeCommandsInBuffer:icb
                      indirectBuffer:rangeBuf
                indirectBufferOffset:(NSUInteger)rangeOffset];
}

// ---------- Optional optimization pass (blit encoder side) ----------

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlBlitEncoderOptimizeIndirectCommandBuffer(
        JNIEnv *, jclass, jlong blitEncoderHandle,
        jlong icbHandle, jint rangeStart, jint rangeLength) {
    if (blitEncoderHandle == 0 || icbHandle == 0 || rangeLength <= 0) return;
    id<MTLBlitCommandEncoder> encoder = voxy_handle_cast<id<MTLBlitCommandEncoder>>(blitEncoderHandle);
    id<MTLIndirectCommandBuffer> icb = voxy_handle_cast<id<MTLIndirectCommandBuffer>>(icbHandle);
    NSRange range = NSMakeRange((NSUInteger)rangeStart, (NSUInteger)rangeLength);
    [encoder optimizeIndirectCommandBuffer:icb withRange:range];
}
