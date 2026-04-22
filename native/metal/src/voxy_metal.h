// voxy_metal.h — shared internal header for libvoxy_metal.
// Only included from *.mm translation units.

#pragma once

#import <Foundation/Foundation.h>
#import <Metal/Metal.h>
#include <jni.h>
#include <cstdint>

// Handles are opaque jlong values that carry a +1-retained Objective-C pointer.
// We cast to/from __bridge_retained / __bridge_transfer to move ownership across
// the JNI boundary, mirroring the semantics of mtlRetain/mtlRelease in Java.

static inline jlong voxy_handle_from(id object) {
    // Transfer ownership: the returned handle holds a +1 retain; Java must call
    // mtlRelease() when finished.
    return (jlong)(__bridge_retained void *)object;
}

template <typename T>
static inline T voxy_handle_cast(jlong handle) {
    // Read-only view; caller does not take ownership.
    return (__bridge T)(void *)handle;
}

// Decrements retain count without destroying the pointer on the Java side.
static inline void voxy_release(jlong handle) {
    if (handle == 0) return;
    CFRelease((void *)handle);
}

static inline void voxy_retain(jlong handle) {
    if (handle == 0) return;
    CFRetain((void *)handle);
}

// Last compile error is shared between Device.newLibraryWithSource and the
// corresponding getter. Protected by @synchronized in the setter.
extern NSString *g_voxy_last_compile_error;

static inline void voxy_set_last_error(NSString *err) {
    @synchronized([NSString class]) {
        g_voxy_last_compile_error = [err copy];
    }
}

// ---- Metal constants that must match MetalNative.java ----
// (Apple's MTLPixelFormat etc. enums are stable, but we keep these pinned so
// drift in the Java side is obvious in code review.)
enum VoxyMTLStorageMode : int {
    VoxyStorageModeShared  = 0,
    VoxyStorageModeManaged = 1,
    VoxyStorageModePrivate = 2,
};

enum VoxyMTLTextureType : int {
    VoxyTextureType2D   = 2,
    VoxyTextureType3D   = 4,
    VoxyTextureTypeCube = 5,
};
