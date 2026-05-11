// voxy_metal_iosurface.mm — IOSurface bridge JNI methods (M10).
//
// IOSurface is the macOS framework for sharing GPU-backed memory across
// processes and across graphics APIs. Voxy uses it to bridge between MC's
// OpenGL context (which composes Voxy's far-distance LOD into the final
// frame) and Voxy's Metal context (which does the actual LOD rendering on
// Apple Silicon, where GL is frozen at 4.1).
//
// Two-sided plumbing:
//   1. Allocate an IOSurface with `IOSurfaceCreate`. We hand-roll the
//      properties dictionary so the surface is portable.
//   2. Wrap the IOSurface as an MTLTexture via
//      `[device newTextureWithDescriptor:iosurface:plane:]`. Metal renders
//      into this texture as a normal color/depth target.
//   3. GL side: the JNI mirror in voxy_metal_iosurface_gl.mm (future)
//      uses `CGLTexImageIOSurface2D` to wrap the same IOSurface as a GL
//      texture. MC's framebuffer composes from there.
//   4. Sync: an MTLSharedEvent (already in voxy_metal_render.mm) pairs
//      with `glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE)` so the GL side
//      doesn't sample mid-render.
//
// This file covers (1) + (2). The GL roundtrip lives in a separate file
// since it needs CGL, not Metal.

#include "voxy_metal.h"
#import <IOSurface/IOSurface.h>

// ---------- IOSurface allocation / release ----------

extern "C" JNIEXPORT jlong JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_iosurfaceCreate(
        JNIEnv *, jclass,
        jint width, jint height, jint pixelFormat, jint bytesPerElement) {
    if (width <= 0 || height <= 0 || bytesPerElement <= 0) return 0;

    NSDictionary *props = @{
        (__bridge NSString *)kIOSurfaceWidth:           @(width),
        (__bridge NSString *)kIOSurfaceHeight:          @(height),
        (__bridge NSString *)kIOSurfacePixelFormat:     @(pixelFormat),
        (__bridge NSString *)kIOSurfaceBytesPerElement: @(bytesPerElement),
        // bytesPerRow auto-computed by IOSurfaceCreate as
        // width * bytesPerElement (rounded up to alignment).
    };
    IOSurfaceRef surface = IOSurfaceCreate((__bridge CFDictionaryRef)props);
    if (surface == NULL) return 0;

    // Return the raw IOSurfaceRef as jlong. IOSurfaceRef is a CFTypeRef under
    // the hood — CFRetain semantics — so we treat it like the other handles
    // (Java side calls iosurfaceRelease to decrement the refcount).
    return (jlong)(uintptr_t)surface;
}

extern "C" JNIEXPORT void JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_iosurfaceRelease(
        JNIEnv *, jclass, jlong handle) {
    if (handle == 0) return;
    IOSurfaceRef surface = (IOSurfaceRef)(uintptr_t)handle;
    CFRelease(surface);
}

extern "C" JNIEXPORT jint JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_iosurfaceGetWidth(
        JNIEnv *, jclass, jlong handle) {
    if (handle == 0) return 0;
    IOSurfaceRef surface = (IOSurfaceRef)(uintptr_t)handle;
    return (jint)IOSurfaceGetWidth(surface);
}

extern "C" JNIEXPORT jint JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_iosurfaceGetHeight(
        JNIEnv *, jclass, jlong handle) {
    if (handle == 0) return 0;
    IOSurfaceRef surface = (IOSurfaceRef)(uintptr_t)handle;
    return (jint)IOSurfaceGetHeight(surface);
}

extern "C" JNIEXPORT jint JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_iosurfaceGetBytesPerRow(
        JNIEnv *, jclass, jlong handle) {
    if (handle == 0) return 0;
    IOSurfaceRef surface = (IOSurfaceRef)(uintptr_t)handle;
    return (jint)IOSurfaceGetBytesPerRow(surface);
}

// ---------- IOSurface → MTLTexture wrapping ----------
//
// The texture is created from a fresh descriptor each time so callers can
// pick pixel format / usage / storage independently of the IOSurface's
// own pixel format (Metal coerces between compatible layouts). Storage
// mode is forced to Private because IOSurface-backed Metal textures must
// be Private — Shared/Managed don't apply when the memory is externally
// managed.

extern "C" JNIEXPORT jlong JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_mtlDeviceNewTextureWithIOSurface(
        JNIEnv *, jclass,
        jlong deviceHandle, jlong iosurfaceHandle,
        jint pixelFormat, jint width, jint height, jint usage) {
    if (deviceHandle == 0 || iosurfaceHandle == 0) return 0;
    id<MTLDevice> device = voxy_handle_cast<id<MTLDevice>>(deviceHandle);
    IOSurfaceRef surface = (IOSurfaceRef)(uintptr_t)iosurfaceHandle;

    MTLTextureDescriptor *desc = [MTLTextureDescriptor
            texture2DDescriptorWithPixelFormat:(MTLPixelFormat)pixelFormat
                                         width:(NSUInteger)width
                                        height:(NSUInteger)height
                                     mipmapped:NO];
    desc.usage = (MTLTextureUsage)usage;
    desc.storageMode = MTLStorageModePrivate;

    id<MTLTexture> texture = [device newTextureWithDescriptor:desc
                                                    iosurface:surface
                                                        plane:0];
    if (texture == nil) return 0;
    return voxy_handle_from(texture);
}
