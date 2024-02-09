#version 430

in vec3 pos;
in vec4 _metadata;
in vec2 uv;

layout(location=1) uniform mat4 transform;
out vec2 texCoord;

void main() {
    uvec4 metadata = uvec4(_metadata*255);
    uint metadata = (metadata.r<<16)|(metadata.g<<8)|(metadata.b);

    gl_Position = transform * vec4(pos, 1.0);
    texCoord = uv;
}
