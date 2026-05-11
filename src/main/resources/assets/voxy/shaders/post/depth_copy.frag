#version 430 core

// M9 migration: bumped to #version 430 (was 330) for valid
// layout(binding=N) on UBOs. The sampler binding=0 below was already
// non-standard at 330 but worked on most drivers; the version bump
// makes it spec-clean.

layout(binding = 0) uniform sampler2D depthTex;

// M9 migration: location-based uniform wrapped in a UBO push block so
// the shader compiles on Metal/Vulkan. GL backend pushes via
// FullscreenBlit.setBytes → glBindBufferRange(GL_UNIFORM_BUFFER, ...).
#ifndef PUSH_BINDING
#define PUSH_BINDING 14
#endif
layout(binding = PUSH_BINDING, std140) uniform Push {
    vec2 scaleFactor;
};

in vec2 UV;
void main() {
    gl_FragDepth = texture(depthTex, UV*scaleFactor).r;
}
