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
