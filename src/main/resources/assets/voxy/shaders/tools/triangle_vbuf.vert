#version 460 core

// M9-prep smoke test vertex shader: reads vertex inputs from an actual
// vertex buffer (instead of generating positions from gl_VertexIndex like
// triangle.vert). Exercises VertexLayout + bindVertexBuffer + the Metal
// MTLVertexDescriptor JNI path.

layout(location = 0) in vec2 inPos;
layout(location = 1) in vec3 inColor;

layout(location = 0) out vec3 vColor;

void main() {
    gl_Position = vec4(inPos, 0.0, 1.0);
    vColor = inColor;
}
