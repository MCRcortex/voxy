#version 460 core
layout(binding = 0) uniform sampler2D blockModelAtlas;

layout(location = 0) in vec2 uv;
layout(location = 1) in flat vec2 baseUV;
layout(location = 2) in flat vec4 colourTinting;

layout(location = 0) out vec4 outColour;
void main() {
    vec2 uv = mod(uv, vec2(1))*(1f/(vec2(3,2)*256f));

    outColour = texture(blockModelAtlas, uv + baseUV) * colourTinting;
}