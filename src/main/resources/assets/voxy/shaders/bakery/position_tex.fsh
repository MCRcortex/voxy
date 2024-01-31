#version 430

layout(location=0) uniform sampler2D tex;
in vec2 texCoord;
out vec4 colour;
void main() {
    colour = texture(tex, texCoord);
}
