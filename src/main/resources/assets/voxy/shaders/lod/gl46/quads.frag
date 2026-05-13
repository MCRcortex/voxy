#version 460 core
//Use quad shuffling to compute fragment mip
//#extension GL_KHR_shader_subgroup_quad: enable
// M9 Phase 2 patch: bumped to #version 460 so gl_HelperInvocation is a
// core built-in. shaderc/glslang's Vulkan profile doesn't accept
// GL_ARB_shader_helper_invocation as an extension; 4.50+ has it built-in.
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

#ifdef PATCHED_SHADER
vec2 getLightmap() {
    return clamp(vec2((interData.y>>4)&0xFu, interData.y&0xFu)/15, vec2(8.0f/256), vec2(248.0f/256));
}
#endif

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

vec4 computeColour(vec2 texturePos, vec4 colour) {
    //Conditional tinting, TODO: FIXME: this is better but still not great, try encode data into the top bit of alpha so its per pixel

    uint tintingFunction = tintingState();
    bool doTint = tintingFunction==2;//Always tint if function == 2
    if (tintingFunction == 1) {//partial tint
        vec4 tintTest = textureLod(blockModelAtlas, texturePos, 0);
        if (abs(tintTest.r-tintTest.g) < 0.02f && abs(tintTest.g-tintTest.b) < 0.02f) {
            doTint = true;
        }
    }
    if (doTint) {
        colour *= uint2vec4RGBA(interData.z).yzwx;
    }
    return (colour * uint2vec4RGBA(interData.y)) + vec4(0,0,0,float(interData.w&0xFFu)/255);
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

#ifdef VOXY_NO_ATLAS
    // M12 Metal path: ModelTextureBakery is still GL-only, so the
    // blockModelAtlas + depthBoundingBuffer textures aren't populated /
    // bound. Skip atlas sampling and emit a deterministic per-quad
    // debug color hashed from `interData.x` (a flat varying carrying the
    // model id + face + flags — varies per quad / section). Then modulate
    // by the *real* MC lightmap colour packed into `interData.y` by the
    // vertex shader (see makeRemainingAttributes in quad_util.glsl —
    // it samples lightSampler and multiplies by computeDirectionalFaceTint,
    // which already encodes UP/DOWN/Z/X face shade from MC's level). M13
    // chunk 2 wired the lightmap sampler on Metal, so this is the real
    // lighting; the synthetic face-Lambertian shade from M12 is gone.
    // Drops the depth-bounding and alpha-discard checks that depend on
    // the unbound atlas textures. `gl_InstanceID` lives only in the
    // vertex stage so we can't use it here; interData.x gives sufficient
    // variation.
    {
        uint hash = interData.x * 2654435761u;
        hash ^= hash >> 13;
        hash *= 1274126177u;
        hash ^= hash >> 16;
        // Map the hash channels into [0.55, 1.0] so every block reads as
        // a saturated bright colour. The plain `(hash & 0xFF) / 255` from
        // earlier let random channels collapse near zero, which combined
        // with lightmap shading would have collapsed too dark for many
        // blocks. Bias the range so the visual contrast is always strong.
        colour = vec4(
            float((hash >>  0) & 0xFFu) / 255.0 * 0.45 + 0.55,
            float((hash >>  8) & 0xFFu) / 255.0 * 0.45 + 0.55,
            float((hash >> 16) & 0xFFu) / 255.0 * 0.45 + 0.55,
            1.0
        );
        // Modulate by the real lightmap + face-shade tinting baked into
        // interData.y by makeRemainingAttributes. Keep alpha at 1.0 —
        // interData.y's alpha channel carries packed face/lod metadata
        // for the non-translucent path (see `addin` in quad_util.glsl)
        // and would zero the fragment.
        colour.rgb *= uint2vec4RGBA(interData.y).rgb;

        // Procedural per-pixel pattern — gives each face a "textured"
        // look instead of a solid colour. Combines a small-grid checker
        // (4x4 cells per quad) with a value-noise speckle so flat block
        // colours read as 3D-textured surfaces. Real model textures from
        // ModelTextureBakery are still M13 chunk 1 — this is the
        // VOXY_NO_ATLAS debug visualization until that lands.
#ifndef USE_NV_BARRY
        {
            uint face = getFace();
            // 4x4 grid checker — gives subtle "tile" structure.
            ivec2 cell = ivec2(floor(uv * 4.0));
            float checker = ((cell.x ^ cell.y) & 1) == 0 ? 1.0 : 0.85;
            // Value-noise speckle — high-frequency variation hiding the
            // flat per-quad fill. Hash from (uv * 16, face) so the
            // pattern is stable per-pixel but uncorrelated across faces.
            vec2 noiseInput = uv * 16.0 + float(face) * 17.0;
            float noise = fract(sin(dot(noiseInput, vec2(12.9898, 78.233))) * 43758.5453);
            float speckle = 0.88 + 0.12 * noise;
            colour.rgb *= checker * speckle;
        }
#endif
    }
#else
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
#endif

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

#ifndef VOXY_NO_DEPTH_BOUND
    //Check the minimum bounding texture and ensure we are greater than it.
    // M13 chunk 1 split: this used to live under `#ifndef VOXY_NO_ATLAS` so
    // the atlas-disabled debug path also skipped the depth-bounding check.
    // Splitting them lets Metal sample the real atlas while still skipping
    // the depth-bounding check (depthTex is M13 chunk 3 — MC depth import
    // hasn't landed yet, so the texture would be unbound and the check
    // would discard everything).
    if (gl_FragCoord.z < texelFetch(depthTex, ivec2(gl_FragCoord.xy), 0).r) {
        discard;
        return;
    }
#endif // VOXY_NO_DEPTH_BOUND

#ifndef VOXY_NO_ATLAS
    //Also, small quad is really fking over the mipping level somehow
    #ifndef TRANSLUCENT
    if (useDiscard() && (textureLod(blockModelAtlas, texPos, 0).a <= 0.1f)) {
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
#endif // VOXY_NO_ATLAS — closes the alpha-discard block above

    #ifndef PATCHED_SHADER_ALLOW_DERIVATIVES
    if (gl_HelperInvocation) {
        return;
    }
    #endif

    #ifndef PATCHED_SHADER
#ifdef VOXY_NO_ATLAS
    // Already computed a debug colour up top; skip computeColour (which
    // re-samples blockModelAtlas via textureLod). Emit straight to outColour.
    outColour = colour;
#else
    colour = computeColour(texPos, colour);
    outColour = colour;
#endif

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
    bool doTint = tintingFunction==2;//Always tint if function == 2
    if (tintingFunction==1) {//Partial tint
        vec4 tintTest = texture(blockModelAtlas, texPos, -2);
        if (abs(tintTest.r-tintTest.g) < 0.02f && abs(tintTest.g-tintTest.b) < 0.02f) {
            doTint = true;
        }
    }
    vec4 tint = vec4(1);
    if (doTint) {
        tint = uint2vec4RGBA(interData.z).yzwx;
    }

    uint face = getFace();
    face ^= uint((face&1u)!=uint(gl_FrontFacing!=((face>>1)!=0u)));
    voxy_emitFragment(VoxyFragmentParameters(colour, tile, texPos, face, modelId, getLightmap(), tint, model.customId));

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

