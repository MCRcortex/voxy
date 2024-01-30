#version 460 core
layout(binding = 0) uniform sampler2D blockModelAtlas;

//TODO: need to fix when merged quads have discardAlpha set to false but they span multiple tiles
// however they are not a full block

layout(location = 0) in vec2 uv;
layout(location = 1) in flat vec2 baseUV;
layout(location = 2) in flat vec4 colourTinting;
layout(location = 3) in flat uint discardAlpha;

layout(location = 0) out vec4 outColour;
void main() {
    vec2 uv = mod(uv, vec2(1))*(1f/(vec2(3,2)*256f));
    vec4 colour = texture(blockModelAtlas, uv + baseUV);
    if (discardAlpha == 1 && colour.a <= 0.001f) {
        discard;
    }
    outColour = colour * colourTinting;

    //outColour = vec4(uv + baseUV, 0, 1);
}