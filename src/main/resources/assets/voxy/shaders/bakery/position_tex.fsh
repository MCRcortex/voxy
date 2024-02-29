#version 430

layout(location=0) uniform sampler2D tex;
in vec2 texCoord;
in flat uint metadata;
out vec4 colour;
void main() {
    colour = texture(tex, texCoord);//*(metadata&1);
}
