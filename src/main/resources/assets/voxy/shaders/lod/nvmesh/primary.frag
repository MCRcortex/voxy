#version 460 core
layout(binding = 0) uniform sampler2D blockModelAtlas;

layout(location=1) in Interpolants {
    vec2 uv;
};

layout(location=2) perprimitiveNV in PerPrimData {
    vec2 baseUV;
    vec4 tinting;
    vec4 addin;
    uint flags;
    vec4 conditionalTinting;
};

layout(location = 0) out vec4 outColour;
void main() {
    vec2 uv = mod(uv, vec2(1.0))*(1.0/(vec2(3.0,2.0)*256.0));
    vec4 colour = texture(blockModelAtlas, uv + baseUV, ((flags>>1)&1u)*-4.0);
    if ((flags&1u) == 1 && colour.a <= 0.25f) {
        discard;
    }

    //Conditional tinting, TODO: FIXME: REPLACE WITH MASK OR SOMETHING, like encode data into the top bit of alpha
    if ((flags&(1u<<2)) != 0 && abs(colour.r-colour.g) < 0.02f && abs(colour.g-colour.b) < 0.02f) {
        colour *= conditionalTinting;
    }

    outColour = (colour * tinting) + addin;
}
