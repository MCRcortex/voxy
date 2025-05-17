#version 430

layout(location=0) in vec4 pos;
layout(location=1) in vec2 uv;

layout(location=1) uniform mat4 transform;
out vec2 texCoord;
out flat uint metadata;

void main() {
    metadata = floatBitsToUint(pos.w);//Fuck you intel

    gl_Position = transform * vec4(pos.xyz, 1.0);
    texCoord = uv;
}
