#version 460 core
layout(binding = 0) uniform sampler2D blockModelAtlas;

//TODO: need to fix when merged quads have discardAlpha set to false but they span multiple tiles
// however they are not a full block

layout(location = 0) in vec2 uv;
layout(location = 1) in flat vec2 baseUV;
layout(location = 2) in flat vec4 tinting;
layout(location = 3) in flat vec4 addin;
layout(location = 4) in flat uint flags;
layout(location = 5) in flat vec4 conditionalTinting;
layout(location = 6) in flat uint meshlet;
//layout(location = 6) in flat vec4 solidColour;

layout(location = 0) out vec4 outColour;
void main() {
    vec2 uv = mod(uv, vec2(1.0))*(1.0/(vec2(3.0,2.0)*256.0));
    //vec4 colour = solidColour;
    vec4 colour = texture(blockModelAtlas, uv + baseUV, ((flags>>1)&1u)*-4.0);
    if ((flags&1u) == 1 && colour.a <= 0.25f) {
        discard;
    }

    //Conditional tinting, TODO: FIXME: REPLACE WITH MASK OR SOMETHING, like encode data into the top bit of alpha
    if ((flags&(1u<<2)) != 0 && abs(colour.r-colour.g) < 0.02f && abs(colour.g-colour.b) < 0.02f) {
        colour *= conditionalTinting;
    }

    outColour = (colour * tinting) + addin;

    //outColour = vec4(uv + baseUV, 0, 1);


    uint hash = meshlet*1231421+123141;
    hash ^= hash>>16;
    hash = hash*1231421+123141;
    hash ^= hash>>16;
    hash = hash * 1827364925 + 123325621;
    //outColour = vec4(float(hash&15u)/15, float((hash>>4)&15u)/15, float((hash>>8)&15u)/15, 1);
}