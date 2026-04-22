// voxy_metal_jni.mm — entry points shared across the library.
// Per-domain JNI methods live in voxy_metal_{device,buffer,texture,memutil}.mm.

#include "voxy_metal.h"

NSString *g_voxy_last_compile_error = nil;

// -------- Generic retain/release --------

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRetain(
        JNIEnv *, jclass, jlong handle) {
    voxy_retain(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlRelease(
        JNIEnv *, jclass, jlong handle) {
    voxy_release(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlSetLabel(
        JNIEnv *env, jclass, jlong handle, jstring label) {
    if (handle == 0 || label == nullptr) return;
    const char *utf = env->GetStringUTFChars(label, nullptr);
    if (!utf) return;
    NSString *nsLabel = [NSString stringWithUTF8String:utf];
    env->ReleaseStringUTFChars(label, utf);

    id<MTLResource> resource = voxy_handle_cast<id<MTLResource>>(handle);
    if ([resource respondsToSelector:@selector(setLabel:)]) {
        [resource setLabel:nsLabel];
    }
}

// -------- Last compile error --------

extern "C" JNIEXPORT jstring JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlGetLastCompileError(
        JNIEnv *env, jclass) {
    NSString *copy;
    @synchronized([NSString class]) {
        copy = [g_voxy_last_compile_error copy];
    }
    if (copy == nil) return nullptr;
    return env->NewStringUTF([copy UTF8String]);
}
