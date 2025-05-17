#version 460 core
layout(binding = 0) uniform sampler2D blockModelAtlas;
layout(binding = 2) uniform sampler2D depthTex;

//#define DEBUG_RENDER

//TODO: need to fix when merged quads have discardAlpha set to false but they span multiple tiles
// however they are not a full block

layout(location = 0) in vec2 uv;
layout(location = 1) in flat vec2 baseUV;
layout(location = 2) in flat vec4 tinting;
layout(location = 3) in flat vec4 addin;
layout(location = 4) in flat uint flags;
layout(location = 5) in flat vec4 conditionalTinting;


#ifdef DEBUG_RENDER
layout(location = 6) in flat uint quadDebug;
#endif
layout(location = 0) out vec4 outColour;
void main() {
    //Check the minimum bounding texture and ensure we are greater than it
    if (gl_FragCoord.z < texelFetch(depthTex, ivec2(gl_FragCoord.xy), 0).r) {
        discard;
    }
    vec2 uv = mod(uv, vec2(1.0))*(1.0/(vec2(3.0,2.0)*256.0));
    vec2 texPos = uv + baseUV;
    //vec4 colour = solidColour;
    //TODO: FIXME, need to manually compute the mip colour
    vec4 colour = texture(blockModelAtlas, texPos, ((flags>>1)&1u)*-5.0);//TODO: FIXME mipping needs to be fixed so that it doesnt go cross model bounds
    //Also, small quad is really fking over the mipping level somehow
    if ((flags&1u) == 1 && (texture(blockModelAtlas, texPos, -16.0).a <= 0.1f)) {
        //This is stupidly stupidly bad for divergence
        //TODO: FIXME, basicly what this do is sample the exact pixel (no lod) for discarding, this stops mipmapping fucking it over
        #ifndef DEBUG_RENDER
        discard;
        #endif
    }

    //Conditional tinting, TODO: FIXME: REPLACE WITH MASK OR SOMETHING, like encode data into the top bit of alpha
    if ((flags&(1u<<2)) != 0 && abs(colour.r-colour.g) < 0.02f && abs(colour.g-colour.b) < 0.02f) {
        colour *= conditionalTinting;
    }

    outColour = (colour * tinting) + addin;

    //outColour = vec4(uv + baseUV, 0, 1);


    #ifdef DEBUG_RENDER
    uint hash = quadDebug*1231421+123141;
    hash ^= hash>>16;
    hash = hash*1231421+123141;
    hash ^= hash>>16;
    hash = hash * 1827364925 + 123325621;
    outColour = vec4(float(hash&15u)/15, float((hash>>4)&15u)/15, float((hash>>8)&15u)/15, 1);
    #endif
}