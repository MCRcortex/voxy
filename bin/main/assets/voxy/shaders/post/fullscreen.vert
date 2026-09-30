#version 330 core

#import <voxy:util/depthutils.glsl>

out vec2 UV;
void main() {
    gl_Position = vec4(vec2(gl_VertexID&1, (gl_VertexID>>1)&1) * 2 - 1, FAR+CLOSER_SIGN*(1.0f/(1<<23)), 1);
    UV = gl_Position.xy*0.5+0.5;
}