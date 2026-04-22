# libvoxy_metal

Native Metal backend for Voxy on Apple Silicon (M-series) Macs.

Builds a `libvoxy_metal.dylib` that implements the JNI methods declared in
`me.cortex.voxy.client.core.metal.MetalNative`. When present, the Java side
activates the Metal render backend automatically; otherwise Voxy falls back
to OpenGL.

## Requirements

- macOS 12+ on Apple Silicon (`arm64`)
- Xcode command-line tools (`xcode-select --install`)
- CMake >= 3.20 (`brew install cmake`)
- JDK 21 (same JDK Voxy is built against)

## Build

From this directory:

```bash
./build.sh           # Release build (default)
./build.sh Debug     # Debug build
```

The script runs CMake and installs the resulting dylib at:

```
../../src/main/resources/natives/macos-arm64/libvoxy_metal.dylib
```

`./gradlew build` invokes `buildMetalNative` automatically on macOS aarch64,
so `./build.sh` is only needed for development iteration outside Gradle.

## Scope (MVP)

This first iteration implements the foundational JNI entry points:
`MTLDevice`, `MTLCommandQueue`, `MTLBuffer`, `MTLTexture`, blit encoder, event
primitives, and MSL library/PSO creation. Render encoders, indirect command
buffers, and the full shader translation pipeline (GLSL → SPIRV → MSL) are
deferred to the next phases.

Handles are opaque `jlong` values holding +1-retained Objective-C pointers.
The Java side must call `mtlRelease()` to free resources. ARC is enabled in
the dylib (`-fobjc-arc`); we use `__bridge_retained`/`CFRelease` to move
ownership across the JNI boundary.
