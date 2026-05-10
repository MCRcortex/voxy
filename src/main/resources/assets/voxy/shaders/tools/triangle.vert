#version 460 core

// M5 smoke test: emits a 3-vertex triangle from hardcoded positions, indexed
// by gl_VertexID. No vertex buffer needed — the host issues drawPrimitives
// with vertexCount=3 and the GPU reads positions from the constant array.
//
// Lives under assets/voxy/shaders/tools/ to mark it as not part of the
// production rendering pipeline.

const vec2 POSITIONS[3] = vec2[3](
    vec2(-0.6, -0.5),
    vec2( 0.6, -0.5),
    vec2( 0.0,  0.7)
);

const vec3 COLORS[3] = vec3[3](
    vec3(1.0, 0.2, 0.2),
    vec3(0.2, 1.0, 0.2),
    vec3(0.2, 0.2, 1.0)
);

layout(location = 0) out vec3 vColor;

void main() {
    int idx = gl_VertexID % 3;
    gl_Position = vec4(POSITIONS[idx], 0.0, 1.0);
    vColor = COLORS[idx];
}
