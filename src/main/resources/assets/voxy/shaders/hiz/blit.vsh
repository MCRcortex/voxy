#version 450

layout(location = 0) out vec2 uv;
void main() {
    vec2 corner = vec2[](vec2(0,0), vec2(1,0), vec2(1,1),vec2(0,1))[gl_VertexID];
    uv = corner;
    gl_Position = vec4(corner*2-1, 0, 1);
}