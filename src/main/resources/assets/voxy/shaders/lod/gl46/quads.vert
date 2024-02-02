#version 460 core
#extension GL_ARB_gpu_shader_int64 : enable

#import <voxy:lod/gl46/quad_format.glsl>
#import <voxy:lod/gl46/bindings.glsl>
#import <voxy:lod/gl46/block_model.glsl>
#line 8

layout(location = 0) out vec2 uv;
layout(location = 1) out flat vec2 baseUV;
layout(location = 2) out flat vec4 tinting;
layout(location = 3) out flat vec4 addin;
layout(location = 4) out flat uint discardAlpha;

uint extractLodLevel() {
    return uint(gl_BaseInstance)>>29;
}

//Note the last 2 bits of gl_BaseInstance are unused
//Gives a relative position of +-255 relative to the player center in its respective lod
ivec3 extractRelativeLodPos() {
    return (ivec3(gl_BaseInstance)<<ivec3(3,12,21))>>ivec3(23);
}

vec4 uint2vec4RGBA(uint colour) {
    return vec4((uvec4(colour)>>uvec4(24,16,8,0))&uvec4(0xFF))/255;
}

//Gets the face offset with respect to the face direction (e.g. some will be + some will be -)
float getDepthOffset(uint faceData, uint face) {
    float offset = extractFaceIndentation(faceData);
    return offset * (1-((int(face)&1)*2));
}

vec2 getFaceSizeOffset(uint faceData, uint corner) {
    vec4 faceOffsetsSizes = extractFaceSizes(faceData);
    return mix(faceOffsetsSizes.xz, -(1-faceOffsetsSizes.yw), bvec2(((corner>>1)&1)==1, (corner&1)==1));
}

//TODO: add a mechanism so that some quads can ignore backface culling
// this would help alot with stuff like crops as they would look kinda weird i think,
// same with flowers etc
void main() {
    int cornerIdx = gl_VertexID&3;
    Quad quad = quadData[uint(gl_VertexID)>>2];
    vec3 innerPos = extractPos(quad);
    uint face = extractFace(quad);
    uint modelId = extractStateId(quad);
    BlockModel model = modelData[modelId];
    uint faceData = model.faceData[face];

    //Change the ordering due to backface culling
    //NOTE: when rendering, backface culling is disabled as we simply dispatch calls for each face
    // this has the advantage of having "unassigned" geometry, that is geometry where the backface isnt culled
    //if (face == 0 || (face>>1 != 0 && (face&1)==1)) {
    //    cornerIdx ^= 1;
    //}

    uint lodLevel = extractLodLevel();
    ivec3 lodCorner = ((extractRelativeLodPos()<<lodLevel) - (baseSectionPos&(ivec3((1<<lodLevel)-1))))<<5;
    vec3 corner = innerPos * (1<<lodLevel) + lodCorner;

    vec2 faceOffset = getFaceSizeOffset(faceData, cornerIdx);
    ivec2 quadSize = extractSize(quad);
    vec2 respectiveQuadSize = vec2(quadSize * ivec2((cornerIdx>>1)&1, cornerIdx&1));
    vec2 size = (respectiveQuadSize + faceOffset) * (1<<lodLevel);

    vec3 offset = vec3(size, (float(face&1) + getDepthOffset(faceData, face)) * (1<<lodLevel));

    if ((face>>1) == 0) { //Up/down
        offset = offset.xzy;
    }
    //Not needed, here for readability
    //if ((face>>1) == 1) {//north/south
    //    offset = offset.xyz;
    //}
    if ((face>>1) == 2) { //west/east
        offset = offset.zxy;
    }

    gl_Position = MVP * vec4(corner + offset, 1);


    //Compute the uv coordinates
    vec2 modelUV = vec2(modelId&0xFF, (modelId>>8)&0xFF)*(1f/(256f));
    //TODO: make the face orientated by 2x3 so that division is not a integer div and modulo isnt needed
    // as these are very slow ops
    baseUV = modelUV + (vec2(face%3, face/3) * (1f/(vec2(3, 2)*256f)));
    uv = respectiveQuadSize + faceOffset;//Add in the face offset for 0,0 uv

    discardAlpha = faceHasAlphaCuttout(faceData);

    //We need to have a conditional override based on if the model size is < a full face + quadSize > 1
    discardAlpha |= uint(any(greaterThan(quadSize, ivec2(1)))) & faceHasAlphaCuttoutOverride(faceData);

    //Compute lighting
    tinting = getLighting(extractLightId(quad));

    //Apply model colour tinting
    uint tintColour = model.colourTint;
    if (modelHasBiomeLUT(model)) {
        tintColour = colourData[tintColour + extractBiomeId(quad)];
    }
    tinting *= uint2vec4RGBA(tintColour).yzwx;
    addin = vec4(0.0);
    if (!modelIsTranslucent(model)) {
        tinting.w = 0.0;
        addin.w = float(face|(lodLevel<<3))/255.0;
    }

    //Apply face tint
    if (face == 0) {
        tinting.xyz *= vec3(0.75, 0.75, 0.75);
    } else if (face != 1) {
        tinting.xyz *= vec3((float(face-2)/4.0)*0.3 + 0.7);
    }


}