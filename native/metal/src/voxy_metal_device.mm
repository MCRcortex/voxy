// voxy_metal_device.mm — MTLDevice, MTLCommandQueue, and basic device info.

#include "voxy_metal.h"

extern "C" JNIEXPORT jlong JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlCreateSystemDefaultDevice(
        JNIEnv *, jclass) {
    id<MTLDevice> device = MTLCreateSystemDefaultDevice();
    if (device == nil) return 0;
    return voxy_handle_from(device);
}

extern "C" JNIEXPORT jlong JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlDeviceNewCommandQueue(
        JNIEnv *, jclass, jlong deviceHandle) {
    if (deviceHandle == 0) return 0;
    id<MTLDevice> device = voxy_handle_cast<id<MTLDevice>>(deviceHandle);
    id<MTLCommandQueue> queue = [device newCommandQueue];
    if (queue == nil) return 0;
    return voxy_handle_from(queue);
}

extern "C" JNIEXPORT jstring JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlDeviceGetName(
        JNIEnv *env, jclass, jlong deviceHandle) {
    if (deviceHandle == 0) return nullptr;
    id<MTLDevice> device = voxy_handle_cast<id<MTLDevice>>(deviceHandle);
    NSString *name = [device name];
    if (name == nil) return nullptr;
    return env->NewStringUTF([name UTF8String]);
}

extern "C" JNIEXPORT jlong JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlDeviceMaxBufferLength(
        JNIEnv *, jclass, jlong deviceHandle) {
    if (deviceHandle == 0) return 0;
    id<MTLDevice> device = voxy_handle_cast<id<MTLDevice>>(deviceHandle);
    if (@available(macOS 10.14, *)) {
        return (jlong)[device maxBufferLength];
    }
    return (jlong)(256LL * 1024 * 1024); // Conservative fallback.
}

extern "C" JNIEXPORT jboolean JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlDeviceSupportsFamily(
        JNIEnv *, jclass, jlong deviceHandle, jint family) {
    if (deviceHandle == 0) return JNI_FALSE;
    id<MTLDevice> device = voxy_handle_cast<id<MTLDevice>>(deviceHandle);
    if (@available(macOS 10.15, *)) {
        return [device supportsFamily:(MTLGPUFamily)family] ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

// -------- Command buffer --------

extern "C" JNIEXPORT jlong JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlCommandQueueNewCommandBuffer(
        JNIEnv *, jclass, jlong queueHandle) {
    if (queueHandle == 0) return 0;
    id<MTLCommandQueue> queue = voxy_handle_cast<id<MTLCommandQueue>>(queueHandle);
    id<MTLCommandBuffer> cmdBuf = [queue commandBuffer];
    if (cmdBuf == nil) return 0;
    return voxy_handle_from(cmdBuf);
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlCommandBufferCommit(
        JNIEnv *, jclass, jlong cmdBufHandle) {
    if (cmdBufHandle == 0) return;
    id<MTLCommandBuffer> cmdBuf = voxy_handle_cast<id<MTLCommandBuffer>>(cmdBufHandle);
    [cmdBuf commit];
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlCommandBufferWaitUntilCompleted(
        JNIEnv *, jclass, jlong cmdBufHandle) {
    if (cmdBufHandle == 0) return;
    id<MTLCommandBuffer> cmdBuf = voxy_handle_cast<id<MTLCommandBuffer>>(cmdBufHandle);
    [cmdBuf waitUntilCompleted];
}

extern "C" JNIEXPORT jint JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlCommandBufferGetStatus(
        JNIEnv *, jclass, jlong cmdBufHandle) {
    if (cmdBufHandle == 0) return 0;
    id<MTLCommandBuffer> cmdBuf = voxy_handle_cast<id<MTLCommandBuffer>>(cmdBufHandle);
    return (jint)[cmdBuf status];
}
