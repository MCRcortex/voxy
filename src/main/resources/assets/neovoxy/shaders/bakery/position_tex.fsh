#version 430

layout(location=0) uniform sampler2D tex;
in vec2 texCoord;
in flat uint metadata;
layout(location=0) out vec4 colour;
layout(location=1) out uvec4 metaOut;

void main() {
    float mipbias = ((~metadata>>1)&1u)*-16.0f;
    mipbias = -16.0f;//Always use the top level
    // TODO FIXME, it should use the 16x16 mipmap, the issue is tho, the alpha cutoff is jsut wrong tho ;-;
    // causing issues with resource packs that are bigger then 16x16 (e.g. faithful x64)
    // this is probably to to the scaleAlphaToCoverage system
    colour = texture(tex, texCoord, mipbias);
    if (colour.a < 0.001f && ((metadata&1u)!=0)) {
        discard;
    }
    metaOut = uvec4((metadata>>2)&1u);//Write if it is or isnt tinted
}
