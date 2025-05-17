#version 330 core

uniform sampler2D text;
out vec4 colour;
in vec2 UV;

void main() {
    colour = texture(text, UV.xy);
    if (colour.a == 0.0) {
        discard;
    }
}