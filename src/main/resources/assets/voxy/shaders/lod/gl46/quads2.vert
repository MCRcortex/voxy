#version 460 core
#extension GL_ARB_gpu_shader_int64 : enable

#import <voxy:lod/quad_format.glsl>
#import <voxy:lod/gl46/bindings.glsl>
#import <voxy:lod/block_model.glsl>
#line 8

//#define DEBUG_RENDER

layout(location = 0) out vec2 uv;
layout(location = 1) out flat vec2 baseUV;
layout(location = 2) out flat vec4 tinting;
layout(location = 3) out flat vec4 addin;
layout(location = 4) out flat uint flags;
layout(location = 5) out flat vec4 conditionalTinting;

#ifdef DEBUG_RENDER
layout(location = 6) out flat uint quadDebug;
#endif

uint extractLodLevel() {
    return uint(gl_BaseInstance)>>27;
}

//Note the last 2 bits of gl_BaseInstance are unused
//Gives a relative position of +-255 relative to the player center in its respective lod
ivec3 extractRelativeLodPos() {
    return (ivec3(gl_BaseInstance)<<ivec3(5,14,23))>>ivec3(23);
}

vec4 uint2vec4RGBA(uint colour) {
    return vec4((uvec4(colour)>>uvec4(24,16,8,0))&uvec4(0xFF))/255.0;
}

vec4 getFaceSize(uint faceData) {
    float EPSILON = 0.001f;
    vec4 faceOffsetsSizes = extractFaceSizes(faceData);
    //Expand the quads by a very small amount
    faceOffsetsSizes.xz -= vec2(EPSILON);
    faceOffsetsSizes.yw += vec2(EPSILON);

    //Make the end relative to the start
    faceOffsetsSizes.yw -= faceOffsetsSizes.xz;

    return faceOffsetsSizes;
}

//TODO: make branchless by using ternaries i think
vec3 swizzelDataAxis(uint axis, vec3 data) {
    if (axis == 0) { //Up/down
        data = data.xzy;
    }
    //Not needed, here for readability
    //if (axis == 1) {//north/south
    //    offset = offset.xyz;
    //}
    if (axis == 2) { //west/east
        data = data.zxy;
    }
    return data;
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
    bool isTranslucent = modelIsTranslucent(model);
    bool hasAO = modelHasMipmaps(model);//TODO: replace with per face AO flag
    bool isShaded = hasAO;//TODO: make this a per face flag

    uint lodLevel = extractLodLevel();


    vec2 modelUV = vec2(modelId&0xFFu, (modelId>>8)&0xFFu)*(1.0/(256.0));
    baseUV = modelUV + (vec2(face>>1, face&1u) * (1.0/(vec2(3.0, 2.0)*256.0)));

    ivec2 quadSize = extractSize(quad);

    { //Generate tinting and flag data
        flags = faceHasAlphaCuttout(faceData);

        //We need to have a conditional override based on if the model size is < a full face + quadSize > 1
        flags |= uint(any(greaterThan(quadSize, ivec2(1)))) & faceHasAlphaCuttoutOverride(faceData);

        flags |= uint(!modelHasMipmaps(model))<<1;

        //Compute lighting
        tinting = getLighting(extractLightId(quad));

        //Apply model colour tinting
        uint tintColour = model.colourTint;
        if (modelHasBiomeLUT(model)) {
            tintColour = colourData[tintColour + extractBiomeId(quad)];
        }

        conditionalTinting = vec4(0);
        if (tintColour != uint(-1)) {
            flags |= 1u<<2;
            conditionalTinting = uint2vec4RGBA(tintColour).yzwx;
        }

        addin = vec4(0.0);
        if (!isTranslucent) {
            tinting.w = 0.0;
            //Encode the face, the lod level and
            uint encodedData = 0;
            encodedData |= face;
            encodedData |= (lodLevel<<3);
            encodedData |= uint(hasAO)<<6;
            addin.w = float(encodedData)/255.0;
        }

        //Apply face tint
        if (isShaded) {
            //TODO: make branchless, infact apply ahead of time to the texture itself in ModelManager since that is
            // per face
            if ((face>>1) == 1) {
                tinting.xyz *= 0.8f;
            } else if ((face>>1) == 2) {
                tinting.xyz *= 0.6f;
            } else if (face == 0){
                tinting.xyz *= 0.5f;
            }
        }
    }





    vec4 faceSize = getFaceSize(faceData);

    vec2 cQuadSize = (faceSize.yw + quadSize - 1) * vec2((cornerIdx>>1)&1, cornerIdx&1);
    uv = faceSize.xz + cQuadSize;

    vec3 cornerPos = extractPos(quad);
    float depthOffset = extractFaceIndentation(faceData);
    cornerPos += swizzelDataAxis(face>>1, vec3(faceSize.xz, mix(depthOffset, 1-depthOffset, float(face&1u))));


    vec3 origin = vec3(((extractRelativeLodPos()<<lodLevel) - (baseSectionPos&(ivec3((1<<lodLevel)-1))))<<5);
    gl_Position = MVP*vec4((cornerPos+swizzelDataAxis(face>>1,vec3(cQuadSize,0)))*(1<<lodLevel)+origin, 1.0);

    #ifdef DEBUG_RENDER
    quadDebug = lodLevel;
    #endif
}