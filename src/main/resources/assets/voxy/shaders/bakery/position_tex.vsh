#version 430

layout(location=0) in vec4 pos;
layout(location=1) in vec2 uv;

// M9 migration: location-based uniform wrapped in a UBO push block so the
// shader compiles on Metal/Vulkan. GL backend pushes via setBytes —
// callers use BudgetBufferRenderer.setMatrixBytes / direct
// glBindBufferRange(GL_UNIFORM_BUFFER, PUSH_BINDING, ...).
#ifndef PUSH_BINDING
#define PUSH_BINDING 14
#endif
layout(binding = PUSH_BINDING, std140) uniform Push {
    mat4 transform;
};

out vec2 texCoord;
out flat uint metadata;

void main() {
    metadata = floatBitsToUint(pos.w);//Fuck you intel

    gl_Position = transform * vec4(pos.xyz, 1.0);
    texCoord = uv;
}
