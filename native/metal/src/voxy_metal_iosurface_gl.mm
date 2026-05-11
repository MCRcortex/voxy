// voxy_metal_iosurface_gl.mm — GL-side of the IOSurface bridge.
//
// The Metal-side allocation lives in voxy_metal_iosurface.mm. To complete the
// round-trip, MC's OpenGL context needs to see the same IOSurface as a normal
// GL texture so its compositor can sample Voxy's far-distance LOD output.
//
// Apple exposes that via `CGLTexImageIOSurface2D` — feed it a CGL context, a
// GL texture name (already glGenTextures'd by Java), and the IOSurfaceRef,
// and the GL driver pins the IOSurface's memory to the texture. Subsequent
// glBindTexture + glDrawElements/glSamplerXxx call sees fresh Metal-rendered
// pixels with no copy.
//
// Sync between contexts is handled separately (MTLSharedEvent ↔
// glClientWaitSync). This file is only the binding step.

#include "voxy_metal.h"

// CGL lives in the OpenGL framework. Including the header pulls in the deprecation
// markers — we deliberately push past them because MoltenVK can't replace this
// path (it's macOS's official GL↔Metal interop, not a Vulkan equivalent).
#define GL_SILENCE_DEPRECATION
#include <OpenGL/OpenGL.h>
#include <OpenGL/gl3.h>
#include <OpenGL/CGLIOSurface.h>

extern "C" JNIEXPORT jboolean JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_cglTexImageIOSurface2D(
        JNIEnv *, jclass,
        jint glTextureName, jint glTarget,
        jint internalFormat, jint width, jint height,
        jint format, jint type,
        jlong iosurfaceHandle, jint plane) {
    if (iosurfaceHandle == 0 || glTextureName == 0) return JNI_FALSE;

    CGLContextObj ctx = CGLGetCurrentContext();
    if (ctx == NULL) {
        // No GL context current — caller responsible for setting one before
        // calling. We can't usefully bind without a context.
        return JNI_FALSE;
    }

    // Bind the texture first so CGLTexImageIOSurface2D's "currently bound
    // texture" target matches the texture name we want to redirect.
    glBindTexture((GLenum)glTarget, (GLuint)glTextureName);

    IOSurfaceRef surface = (IOSurfaceRef)(uintptr_t)iosurfaceHandle;
    CGLError err = CGLTexImageIOSurface2D(ctx,
            (GLenum)glTarget,
            (GLenum)internalFormat,
            (GLsizei)width,
            (GLsizei)height,
            (GLenum)format,
            (GLenum)type,
            surface,
            (GLuint)plane);

    return err == kCGLNoError ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_me_cortex_voxy_client_core_metal_MetalNative_cglGetCurrentContext(
        JNIEnv *, jclass) {
    return (jlong)(uintptr_t)CGLGetCurrentContext();
}
