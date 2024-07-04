#version 430

layout(location=0) in vec3 pos;
layout(location=1) in vec2 uv;
layout(location=2) in vec4 _metadata;

layout(location=1) uniform mat4 transform;
out vec2 texCoord;
out flat uint metadata;

void main() {
    uvec4 meta = uvec4(_metadata*255);
    metadata = (meta.r<<16)|(meta.g<<8)|(meta.b);

    gl_Position = transform * vec4(pos, 1.0);
    texCoord = uv;
}
