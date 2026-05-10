#version 460 core

// M5 smoke test fragment shader: passes through the per-vertex color
// interpolated from triangle.vert.

layout(location = 0) in vec3 vColor;
layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vec4(vColor, 1.0);
}
