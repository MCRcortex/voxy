#version 460 core
layout(binding = 0) uniform sampler2D blockModelAtlas;
layout(location = 0) in vec2 uv;
layout(location = 0) out vec4 outColour;
void main() {
    vec2 uv = mod(uv, vec2(1));

    outColour = texture(blockModelAtlas, uv);
}