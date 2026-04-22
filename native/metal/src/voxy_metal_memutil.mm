// voxy_metal_memutil.mm — raw memory helpers callable from Java.
// These do not depend on Metal at all but ship in the same dylib to avoid a
// second JNI library for two functions.

#include "voxy_metal.h"
#include <cstring>

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_memsetZero(
        JNIEnv *, jclass, jlong addr, jlong size) {
    if (addr == 0 || size <= 0) return;
    std::memset((void *)addr, 0, (size_t)size);
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_memsetInt(
        JNIEnv *, jclass, jlong addr, jint value, jlong count) {
    if (addr == 0 || count <= 0) return;
    uint32_t *dst = (uint32_t *)addr;
    uint32_t v = (uint32_t)value;
    for (jlong i = 0; i < count; ++i) {
        dst[i] = v;
    }
}
