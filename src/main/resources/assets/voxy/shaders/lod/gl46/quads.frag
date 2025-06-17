#version 460 core
//Use quad shuffling to compute fragment mip
//#extension GL_KHR_shader_subgroup_quad: enable


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
layout(location = 6) in flat vec2 quadSize;

#ifdef DEBUG_RENDER
layout(location = 7) in flat uint quadDebug;
#endif
layout(location = 0) out vec4 outColour;

vec4 computeColour(vec4 colour) {
    //Conditional tinting, TODO: FIXME: REPLACE WITH MASK OR SOMETHING, like encode data into the top bit of alpha
    if ((flags&(1u<<2)) != 0 && abs(colour.r-colour.g) < 0.02f && abs(colour.g-colour.b) < 0.02f) {
        colour *= conditionalTinting;
    }
    return (colour * tinting) + addin;
}

bool useMipmaps() {
    return ((flags>>1)&1u)==0u;
}

void main() {
    //Tile is the tile we are in
    vec2 tile;
    vec2 uv2 = modf(uv, tile)*(1.0/(vec2(3.0,2.0)*256.0));
    vec4 colour = vec4(1);
    vec2 texPos = uv2 + baseUV;
    if (useMipmaps()) {
        vec2 uvSmol = uv*(1.0/(vec2(3.0,2.0)*256.0));
        vec2 dx = dFdx(uvSmol);//vec2(lDx, dDx);
        vec2 dy = dFdy(uvSmol);//vec2(lDy, dDy);
        colour = textureGrad(blockModelAtlas, texPos, dx, dy);
    } else {
        colour = texture(blockModelAtlas, texPos, -5.0);
    }

    if (any(notEqual(clamp(tile, vec2(0), quadSize), tile))) {
        discard;
    }

    //Check the minimum bounding texture and ensure we are greater than it
    if (gl_FragCoord.z < texelFetch(depthTex, ivec2(gl_FragCoord.xy), 0).r) {
        discard;
    }


    //Also, small quad is really fking over the mipping level somehow
    if ((flags&1u) == 1 && (texture(blockModelAtlas, texPos, -16.0).a <= 0.1f)) {
        //This is stupidly stupidly bad for divergence
        //TODO: FIXME, basicly what this do is sample the exact pixel (no lod) for discarding, this stops mipmapping fucking it over
        #ifndef DEBUG_RENDER
        discard;
        #endif
    }

    outColour = computeColour(colour);


    #ifdef DEBUG_RENDER
    uint hash = quadDebug*1231421+123141;
    hash ^= hash>>16;
    hash = hash*1231421+123141;
    hash ^= hash>>16;
    hash = hash * 1827364925 + 123325621;
    outColour = vec4(float(hash&15u)/15, float((hash>>4)&15u)/15, float((hash>>8)&15u)/15, 1);
    #endif
}



//#ifdef GL_KHR_shader_subgroup_quad
/*
uint hash = (uint(tile.x)*(1<<16))^uint(tile.y);
uint horiz = subgroupQuadSwapHorizontal(hash);
bool sameTile = horiz==hash;
uint sv = mix(uint(-1), hash, sameTile);
uint vert = subgroupQuadSwapVertical(sv);
sameTile = sameTile&&vert==hash;
mipBias = sameTile?0:-5.0;
*/
/*
vec2 uvSmol = uv*(1.0/(vec2(3.0,2.0)*256.0));
float lDx = subgroupQuadSwapHorizontal(uvSmol.x)-uvSmol.x;
float lDy = subgroupQuadSwapVertical(uvSmol.y)-uvSmol.y;
float dDx = subgroupQuadSwapDiagonal(lDx);
float dDy = subgroupQuadSwapDiagonal(lDy);
vec2 dx = vec2(lDx, dDx);
vec2 dy = vec2(lDy, dDy);
colour = textureGrad(blockModelAtlas, texPos, dx, dy);
*/
//#else
//colour = texture(blockModelAtlas, texPos);
//#endif