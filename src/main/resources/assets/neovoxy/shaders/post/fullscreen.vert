#version 330 core

out vec2 UV;
void main() {
    gl_Position = vec4(vec2(gl_VertexID&1, (gl_VertexID>>1)&1) * 2 - 1, 0.99999999999f, 1);
    UV = gl_Position.xy*0.5+0.5;
}