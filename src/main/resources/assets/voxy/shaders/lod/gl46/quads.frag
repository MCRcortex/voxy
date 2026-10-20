#version 460 core
//Use quad shuffling to compute fragment mip
//#extension GL_KHR_shader_subgroup_quad: enable
#ifdef USE_SINGLE_TRI
#define USE_NV_BARRY
#endif

#ifdef USE_NV_BARRY
#extension GL_NV_fragment_shader_barycentric: require
#endif

layout(binding = 0) uniform sampler2D blockModelAtlas;
layout(binding = 2) uniform sampler2D depthTex;

//#define DEBUG_RENDER

//TODO: need to fix when merged quads have discardAlpha set to false but they span multiple tiles
// however they are not a full block

layout(location = 0) in flat uvec4 interData;
#ifndef USE_NV_BARRY
layout(location = 1) in vec2 uv;
#endif

#ifdef DEBUG_RENDER
layout(location = 7) in flat uint quadDebug;
#endif


#ifndef PATCHED_SHADER
layout(location = 0) out vec4 outColour;
#else

//Bind the model buffer and import the model system as we need it
#define MODEL_BUFFER_BINDING 3
#import <voxy:lod/block_model.glsl>

#endif

#import <voxy:lod/gl46/bindings.glsl>
#import <voxy:lod/lighting.glsl>


#import <voxy:util/depthutils.glsl>


vec4 uint2vec4RGBA(uint colour) {
    return vec4((uvec4(colour)>>uvec4(24,16,8,0))&uvec4(0xFF))/255.0;
}

//bool useMipmaps() {
//    return (interData.x&2u)==0u;
//}

uint tintingState() {
    return (interData.x>>2)&3u;
}

bool useDiscard() {
    return (interData.x&1u)==1u;
}

uint getFace() {
    return (interData.x>>4)&7u;
}


uint getModelId() {
    return interData.x>>16;
}

vec2 getBaseUV() {
    uint face = getFace();
    uint modelId = interData.x>>16;
    vec2 modelUV = vec2(modelId&0xFFu, (modelId>>8)&0xFFu)*(1.0/(256.0));
    return modelUV + (vec2(face>>1, face&1u) * (1.0/(vec2(3.0, 2.0)*256.0)));
}


#ifdef PATCHED_SHADER
struct VoxyFragmentParameters {
    //TODO: pass in derivative data
    vec4 sampledColour;
    vec2 tile;
    vec2 uv;
    uint face;
    uint modelId;
    vec2 lightMap;
    vec4 tinting;
    uint customId;//Same as iris's modelId
};

void voxy_emitFragment(VoxyFragmentParameters parameters);
#else

vec4 computeColour(vec2 texturePos, vec4 colour, float tintMask) {
    uint tintingFunction = tintingState();
    if (tintingFunction == 2u) {
        colour.rgb *= uint2vec4RGBA(interData.z).yzw;
    } else if (tintingFunction == 1u) {
        colour.rgb *= mix(vec3(1.0), uint2vec4RGBA(interData.z).yzw, tintMask);
    }
    return (colour * uint2vec4RGBA(interData.y)) + vec4(0,0,0,float(interData.w&0xFFu)/255.0);
}

#endif


void main() {
    //vec2 uv = vec2(0);
    //Tile is the tile we are in
    vec2 tile;
    #ifdef USE_NV_BARRY
    #ifdef USE_SINGLE_TRI
    if (gl_BaryCoordNV.x>=0.5||gl_BaryCoordNV.y>=0.5) discard;
    vec2 uv = gl_BaryCoordNV.yx*(vec2((interData.x>>8)&0xFu, (interData.x>>12)&0xFu)+1)*2;
    #else
    vec2 uv = mix(gl_BaryCoordNV.yx, 1-gl_BaryCoordNV.xz, gl_PrimitiveID&1)*(vec2((interData.x>>8)&0xFu, (interData.x>>12)&0xFu)+1);
    #endif
    #endif

    vec2 uv2 = modf(uv, tile)*(1.0/(vec2(3.0,2.0)*256.0));
    vec4 colour;
    vec2 texPos = uv2 + getBaseUV();
//This is deprecated, TODO: remove the non mip code path
    //if (useMipmaps())
    {
        vec2 uvSmol = uv*(1.0/(vec2(3.0,2.0)*256.0));
        vec2 dx = dFdx(uvSmol);//vec2(lDx, dDx);
        vec2 dy = dFdy(uvSmol);//vec2(lDy, dDy);
        colour = textureGrad(blockModelAtlas, texPos, dx, dy);
    }// else {
    //    colour = textureLod(blockModelAtlas, texPos, 0);
    //}

    float tintMask = colour.a;

    //If we are in shaders and are a helper invocation, just exit, as it enables extra performance gains for small sized
    // fragments, we do this here after derivative computation
    //Trying it with all shaders
    //#ifdef PATCHED_SHADER
    #ifndef PATCHED_SHADER_ALLOW_DERIVATIVES
    if (gl_HelperInvocation) {
        return;
    }
    #endif
    //#endif

    if (any(notEqual(clamp(tile, vec2(0), vec2((interData.x>>8)&0xFu, (interData.x>>12)&0xFu)), tile))) {
        discard;
        return;
    }

    //Check the minimum bounding texture and ensure we are greater than it
    if (DEPTH_SCALAR_COMPARE(gl_FragCoord.z, texelFetch(depthTex, ivec2(gl_FragCoord.xy), 0).r)) {
        discard;
        return;
    }


    //Also, small quad is really fking over the mipping level somehow
    #ifndef TRANSLUCENT
    colour.a = 1.0f;
    if (useDiscard() && tintingState() != 1u && (textureLod(blockModelAtlas, texPos, 0).a <= 0.1f)) {
    //if (useDiscard() && (colour.a <= 0.1f)) {
    #else
    if (textureLod(blockModelAtlas, texPos, 0).a == 0.0f) {
    #endif
        //This is stupidly stupidly bad for divergence
        //TODO: FIXME, basicly what this do is sample the exact pixel (no lod) for discarding, this stops mipmapping fucking it over
        #ifndef DEBUG_RENDER
        discard;
        return;
        #endif
    }

    #ifndef PATCHED_SHADER_ALLOW_DERIVATIVES
    if (gl_HelperInvocation) {
        return;
    }
    #endif

    #ifndef PATCHED_SHADER
    colour = computeColour(texPos, colour, tintMask);
    outColour = colour;

    #ifdef DEBUG_RENDER
    uint hash = quadDebug*1231421+123141;
    hash ^= hash>>16;
    hash = hash*1231421+123141;
    hash ^= hash>>16;
    hash = hash * 1827364925 + 123325621;
    outColour = vec4(float(hash&15u)/15, float((hash>>4)&15u)/15, float((hash>>8)&15u)/15, 0);
    #endif

    #else
    uint modelId = getModelId();
    BlockModel model = modelData[modelId];
    uint tintingFunction = tintingState();
    vec4 tint = vec4(1.0);
    if (tintingFunction == 2u) {
        tint = uint2vec4RGBA(interData.z).yzwx;
    } else if (tintingFunction == 1u) {
        tint = vec4(mix(vec3(1.0), uint2vec4RGBA(interData.z).yzw, tintMask), 1.0);
    }

    uint face = getFace();
    face ^= uint((face&1u)!=uint(gl_FrontFacing!=((face>>1)!=0u)));
    voxy_emitFragment(VoxyFragmentParameters(colour, tile, texPos, face, modelId, getLightmapUv(interData.y), tint, model.customId));

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

//Undefine the depth stuff
#import <voxy:util/depthutils.glsl>

