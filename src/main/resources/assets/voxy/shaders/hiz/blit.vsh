#version 430

// M9 migration: was a 4-vertex fan (corners in fan order). Metal has no
// TRIANGLE_FAN primitive and emulating one cross-backend is ugly. Reorder
// to strip-order — same quad, valid on GL/Metal/Vulkan as TRIANGLE_STRIP.
// Strip order for a full-screen quad: (0,0)-(1,0)-(0,1)-(1,1).
layout(location = 0) out vec2 uv;
void main() {
    vec2 corner = vec2[](vec2(0,0), vec2(1,0), vec2(0,1), vec2(1,1))[gl_VertexID];
    uv = corner;
    gl_Position = vec4(corner*2-1, 0, 1);
}