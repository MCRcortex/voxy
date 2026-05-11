cmake_minimum_required(VERSION 3.20)
project(voxy_metal LANGUAGES CXX OBJCXX)

if(NOT APPLE)
    message(FATAL_ERROR "libvoxy_metal is only supported on macOS (Apple Silicon / Metal).")
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_OSX_DEPLOYMENT_TARGET "12.0" CACHE STRING "macOS deployment target")

# Require ARM64; Metal backend is MVP-targeted at Apple Silicon only.
if(NOT CMAKE_OSX_ARCHITECTURES)
    set(CMAKE_OSX_ARCHITECTURES "arm64" CACHE STRING "Target architectures" FORCE)
endif()

# -------- JNI (JDK 21) headers --------
# Honor JAVA_HOME if set; otherwise use java_home on macOS.
if(DEFINED ENV{JAVA_HOME})
    set(JAVA_HOME "$ENV{JAVA_HOME}")
else()
    execute_process(
        COMMAND /usr/libexec/java_home
        OUTPUT_VARIABLE JAVA_HOME
        OUTPUT_STRIP_TRAILING_WHITESPACE)
endif()
if(NOT EXISTS "${JAVA_HOME}/include/jni.h")
    message(FATAL_ERROR "Cannot find jni.h under ${JAVA_HOME}. Set JAVA_HOME to a JDK 21 install.")
endif()

# -------- Sources --------
set(VOXY_METAL_SOURCES
    src/voxy_metal_jni.mm
    src/voxy_metal_device.mm
    src/voxy_metal_buffer.mm
    src/voxy_metal_texture.mm
    src/voxy_metal_memutil.mm
    src/voxy_metal_render.mm
    src/voxy_metal_compute.mm
    src/voxy_metal_icb.mm
    src/voxy_metal_iosurface.mm
    src/voxy_metal_iosurface_gl.mm)

add_library(voxy_metal SHARED ${VOXY_METAL_SOURCES})

set_target_properties(voxy_metal PROPERTIES
    OUTPUT_NAME "voxy_metal"
    PREFIX "lib"
    SUFFIX ".dylib"
    MACOSX_RPATH ON)

target_include_directories(voxy_metal PRIVATE
    "${JAVA_HOME}/include"
    "${JAVA_HOME}/include/darwin"
    src)

target_compile_options(voxy_metal PRIVATE
    -fobjc-arc
    -Wall -Wextra -Wno-unused-parameter
    -fvisibility=hidden)

# JNI export must stay visible.
target_compile_definitions(voxy_metal PRIVATE JNIEXPORT=__attribute__\(\(visibility\(\"default\"\)\)\))

find_library(METAL_FRAMEWORK Metal REQUIRED)
find_library(FOUNDATION_FRAMEWORK Foundation REQUIRED)
find_library(QUARTZCORE_FRAMEWORK QuartzCore REQUIRED)
find_library(IOSURFACE_FRAMEWORK IOSurface REQUIRED)
find_library(COREGRAPHICS_FRAMEWORK CoreGraphics REQUIRED)
find_library(OPENGL_FRAMEWORK OpenGL REQUIRED)

target_link_libraries(voxy_metal PRIVATE
    ${METAL_FRAMEWORK}
    ${FOUNDATION_FRAMEWORK}
    ${QUARTZCORE_FRAMEWORK}
    ${IOSURFACE_FRAMEWORK}
    ${COREGRAPHICS_FRAMEWORK}
    ${OPENGL_FRAMEWORK})

# Install to the resources folder so Java can System.loadLibrary from jar or dev run.
set(VOXY_LIB_OUTPUT "${CMAKE_SOURCE_DIR}/../../src/main/resources/natives/macos-arm64")
add_custom_command(TARGET voxy_metal POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E make_directory "${VOXY_LIB_OUTPUT}"
    COMMAND ${CMAKE_COMMAND} -E copy "$<TARGET_FILE:voxy_metal>" "${VOXY_LIB_OUTPUT}/"
    COMMENT "Installing libvoxy_metal.dylib to resources/natives/macos-arm64/")
