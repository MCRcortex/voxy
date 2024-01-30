#version 430

in vec3 pos;
in vec2 uv;

layout(location=1) uniform mat4 transform;
out vec2 texCoord;

void main() {
    gl_Position = transform * vec4(pos, 1.0);
    texCoord = uv;
}
