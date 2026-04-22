// voxy_metal_buffer.mm — MTLBuffer operations.

#include "voxy_metal.h"

extern "C" JNIEXPORT jlong JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlDeviceNewBuffer(
        JNIEnv *, jclass, jlong deviceHandle, jlong size, jint options) {
    if (deviceHandle == 0 || size <= 0) return 0;
    id<MTLDevice> device = voxy_handle_cast<id<MTLDevice>>(deviceHandle);
    id<MTLBuffer> buffer = [device newBufferWithLength:(NSUInteger)size
                                               options:(MTLResourceOptions)options];
    if (buffer == nil) return 0;
    return voxy_handle_from(buffer);
}

extern "C" JNIEXPORT jlong JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlDeviceNewBufferWithData(
        JNIEnv *, jclass, jlong deviceHandle, jlong dataAddr, jlong size, jint options) {
    if (deviceHandle == 0 || size <= 0) return 0;
    id<MTLDevice> device = voxy_handle_cast<id<MTLDevice>>(deviceHandle);
    const void *bytes = (const void *)dataAddr;
    id<MTLBuffer> buffer = bytes
        ? [device newBufferWithBytes:bytes length:(NSUInteger)size options:(MTLResourceOptions)options]
        : [device newBufferWithLength:(NSUInteger)size options:(MTLResourceOptions)options];
    if (buffer == nil) return 0;
    return voxy_handle_from(buffer);
}

extern "C" JNIEXPORT jlong JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlBufferContents(
        JNIEnv *, jclass, jlong bufferHandle) {
    if (bufferHandle == 0) return 0;
    id<MTLBuffer> buffer = voxy_handle_cast<id<MTLBuffer>>(bufferHandle);
    return (jlong)(uintptr_t)[buffer contents];
}

extern "C" JNIEXPORT jlong JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlBufferLength(
        JNIEnv *, jclass, jlong bufferHandle) {
    if (bufferHandle == 0) return 0;
    id<MTLBuffer> buffer = voxy_handle_cast<id<MTLBuffer>>(bufferHandle);
    return (jlong)[buffer length];
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlBufferDidModifyRange(
        JNIEnv *, jclass, jlong bufferHandle, jlong offset, jlong length) {
    if (bufferHandle == 0) return;
    id<MTLBuffer> buffer = voxy_handle_cast<id<MTLBuffer>>(bufferHandle);
    // didModifyRange: is a no-op on Shared storage but must be called on Managed.
    if (buffer.storageMode == MTLStorageModeManaged) {
        [buffer didModifyRange:NSMakeRange((NSUInteger)offset, (NSUInteger)length)];
    }
}

// -------- Blit encoder ops (partial, for zero-fill/copy) --------

extern "C" JNIEXPORT jlong JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlCommandBufferNewBlitEncoder(
        JNIEnv *, jclass, jlong cmdBufHandle) {
    if (cmdBufHandle == 0) return 0;
    id<MTLCommandBuffer> cmdBuf = voxy_handle_cast<id<MTLCommandBuffer>>(cmdBufHandle);
    id<MTLBlitCommandEncoder> enc = [cmdBuf blitCommandEncoder];
    if (enc == nil) return 0;
    return voxy_handle_from(enc);
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlEncoderEndEncoding(
        JNIEnv *, jclass, jlong encoderHandle) {
    if (encoderHandle == 0) return;
    id<MTLCommandEncoder> enc = voxy_handle_cast<id<MTLCommandEncoder>>(encoderHandle);
    [enc endEncoding];
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlBlitEncoderFillBuffer(
        JNIEnv *, jclass, jlong encoderHandle, jlong bufferHandle,
        jlong offset, jlong length, jbyte value) {
    if (encoderHandle == 0 || bufferHandle == 0 || length <= 0) return;
    id<MTLBlitCommandEncoder> enc = voxy_handle_cast<id<MTLBlitCommandEncoder>>(encoderHandle);
    id<MTLBuffer> buffer = voxy_handle_cast<id<MTLBuffer>>(bufferHandle);
    [enc fillBuffer:buffer
              range:NSMakeRange((NSUInteger)offset, (NSUInteger)length)
              value:(uint8_t)value];
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlBlitEncoderCopyBuffer(
        JNIEnv *, jclass, jlong encoderHandle,
        jlong srcHandle, jlong srcOffset,
        jlong dstHandle, jlong dstOffset, jlong size) {
    if (encoderHandle == 0 || srcHandle == 0 || dstHandle == 0 || size <= 0) return;
    id<MTLBlitCommandEncoder> enc = voxy_handle_cast<id<MTLBlitCommandEncoder>>(encoderHandle);
    id<MTLBuffer> src = voxy_handle_cast<id<MTLBuffer>>(srcHandle);
    id<MTLBuffer> dst = voxy_handle_cast<id<MTLBuffer>>(dstHandle);
    [enc copyFromBuffer:src
           sourceOffset:(NSUInteger)srcOffset
               toBuffer:dst
      destinationOffset:(NSUInteger)dstOffset
                   size:(NSUInteger)size];
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlBlitEncoderCopyBufferToTexture(
        JNIEnv *, jclass, jlong encoderHandle,
        jlong srcHandle, jlong srcOffset, jint srcBytesPerRow,
        jlong dstTextureHandle, jint dstSlice, jint dstLevel,
        jint dstX, jint dstY, jint width, jint height) {
    if (encoderHandle == 0 || srcHandle == 0 || dstTextureHandle == 0) return;
    id<MTLBlitCommandEncoder> enc = voxy_handle_cast<id<MTLBlitCommandEncoder>>(encoderHandle);
    id<MTLBuffer> src = voxy_handle_cast<id<MTLBuffer>>(srcHandle);
    id<MTLTexture> dst = voxy_handle_cast<id<MTLTexture>>(dstTextureHandle);
    MTLSize size = MTLSizeMake((NSUInteger)width, (NSUInteger)height, 1);
    MTLOrigin origin = MTLOriginMake((NSUInteger)dstX, (NSUInteger)dstY, 0);
    [enc copyFromBuffer:src
           sourceOffset:(NSUInteger)srcOffset
      sourceBytesPerRow:(NSUInteger)srcBytesPerRow
    sourceBytesPerImage:(NSUInteger)srcBytesPerRow * (NSUInteger)height
             sourceSize:size
              toTexture:dst
       destinationSlice:(NSUInteger)dstSlice
       destinationLevel:(NSUInteger)dstLevel
      destinationOrigin:origin];
}
