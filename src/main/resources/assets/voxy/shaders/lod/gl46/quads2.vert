#version 460 core
#extension GL_ARB_gpu_shader_int64 : enable

#define QUAD_BUFFER_BINDING 1
#define MODEL_BUFFER_BINDING 3
#define MODEL_COLOUR_BUFFER_BINDING 4
#define POSITION_SCRATCH_BINDING 5
#define LIGHTING_SAMPLER_BINDING 1


#import <voxy:lod/quad_format.glsl>
#import <voxy:lod/block_model.glsl>
#import <voxy:lod/gl46/bindings.glsl>

//#define DEBUG_RENDER

layout(location = 0) out flat uvec4 interData;
layout(location = 1) out vec2 uv;

uint packVec4(vec4 vec) {
    uvec4 vec_=uvec4(vec*255)<<uvec4(24,16,8,0);
    return vec_.x|vec_.y|vec_.z|vec_.w;
}

void setSizeAndFlags(uint modelId, uint _flags, ivec2 quadSize) {
    interData.x = (modelId<<16) | _flags | (uint(quadSize.x-1)<<8) | (uint(quadSize.y-1)<<12);
}

void setTintingAndExtra(vec4 _tinting, uint _conditionalTinting, uint addin) {
    interData.y = packVec4(_tinting);
    interData.z = _conditionalTinting;
    interData.w = addin;
}

#ifdef DEBUG_RENDER
layout(location = 7) out flat uint quadDebug;
#endif

/*
uint extractLodLevel() {
    return uint(gl_BaseInstance)>>27;
}

//Note the last 2 bits of gl_BaseInstance are unused
//Gives a relative position of +-255 relative to the player center in its respective lod
ivec3 extractRelativeLodPos() {
    return (ivec3(gl_BaseInstance)<<ivec3(5,14,23))>>ivec3(23);
}*/

vec4 getFaceSize(uint faceData) {
    float EPSILON = 0.00005f;

    vec4 faceOffsetsSizes = extractFaceSizes(faceData);

    //Expand the quads by a very small amount (because of the subtraction after this also becomes an implicit add)
    faceOffsetsSizes.xz -= vec2(EPSILON);

    //Make the end relative to the start
    faceOffsetsSizes.yw -= faceOffsetsSizes.xz;

    return faceOffsetsSizes;
}

vec3 swizzelDataAxis(uint axis, vec3 data) {
    return mix(mix(data.zxy,data.xzy,bvec3(axis==0)),data,bvec3(axis==1));
}

uint extractDetail(uvec2 encPos) {
    return encPos.x>>28;
}

ivec3 extractLoDPosition(uvec2 encPos) {
    int y = ((int(encPos.x)<<4)>>24);
    int x = (int(encPos.y)<<4)>>8;
    int z = int((encPos.x&((1u<<20)-1))<<4);
    z |= int(encPos.y>>28);
    z <<= 8;
    z >>= 8;
    return ivec3(x,y,z);
}


vec2 taaShift();

//TODO: add a mechanism so that some quads can ignore backface culling
// this would help alot with stuff like crops as they would look kinda weird i think,
// same with flowers etc
void main() {
    int cornerIdx = gl_VertexID&3;
    Quad quad = quadData[uint(gl_VertexID)>>2];
    uint face = extractFace(quad);
    uint modelId = extractStateId(quad);
    BlockModel model = modelData[modelId];
    uint faceData = model.faceData[face];
    bool isTranslucent = modelIsTranslucent(model);
    bool hasAO = modelHasMipmaps(model);//TODO: replace with per face AO flag
    bool isShaded = hasAO;//TODO: make this a per face flag


    uvec2 encPos = positionBuffer[gl_BaseInstance];
    uint lodLevel = extractDetail(encPos);

    ivec2 quadSize = extractSize(quad);




    vec4 faceSize = getFaceSize(faceData);

    vec2 cQuadSize = (faceSize.yw + quadSize - 1) * vec2((cornerIdx>>1)&1, cornerIdx&1);
    uv = faceSize.xz + cQuadSize;

    vec3 cornerPos = extractPos(quad);
    float depthOffset = extractFaceIndentation(faceData);
    cornerPos += swizzelDataAxis(face>>1, vec3(faceSize.xz, mix(depthOffset, 1-depthOffset, float(face&1u))));

    vec3 origin = vec3(((extractLoDPosition(encPos)<<lodLevel) - baseSectionPos)<<5);
    vec3 pointPos = (cornerPos+swizzelDataAxis(face>>1,vec3(cQuadSize,0)))*(1<<lodLevel)+origin;
    gl_Position = MVP*vec4(pointPos, 1.0);

    //Apply taa shift
    gl_Position.xy += taaShift()*gl_Position.w;



    if (cornerIdx == 1) //Only if we are the provoking vertex
    {
        //Generate tinting and flag data
        uint flags = faceHasAlphaCuttout(faceData);

        //We need to have a conditional override based on if the model size is < a full face + quadSize > 1
        flags |= uint(any(greaterThan(quadSize, ivec2(1)))) & faceHasAlphaCuttoutOverride(faceData);

        flags |= uint(!modelHasMipmaps(model))<<1;

        //Compute lighting
        uint lighting = extractLightId(quad);
        vec4 tinting = getLighting(extractLightId(quad));

        //Apply model colour tinting
        uint tintColour = model.colourTint;

        if (modelHasBiomeLUT(model)) {
            tintColour = colourData[tintColour + extractBiomeId(quad)];
        }

        uint tintState = faceTintState(faceData);

        uint conditionalTinting = 0;
        if (tintColour != uint(-1)) {
            flags |= tintState<<2;
            conditionalTinting = tintColour;
        }

        setSizeAndFlags(modelId, flags|(face<<4), quadSize);

        #ifndef PATCHED_SHADER
        uint addin = 0;
        if (!isTranslucent) {
            tinting.w = 0.0;
            //Encode the face, the lod level and
            uint encodedData = 0;
            encodedData |= face;
            encodedData |= (lodLevel<<3);
            encodedData |= uint(hasAO)<<6;
            addin = encodedData;
        }

        //Apply face tint
        #ifdef DARKENED_TINTING
        if (isShaded) {
            //TODO: make branchless, infact apply ahead of time to the texture itself in ModelManager since that is
            // per face
            if ((face>>1) == 1) {//NORTH, SOUTH
                tinting.xyz *= 0.8f;
            } else if ((face>>1) == 2) {//EAST, WEST
                tinting.xyz *= 0.6f;
            } else {//UP DOWN
                tinting.xyz *= 0.9f;
            }
        } else {
            tinting.xyz *= 0.9f;
        }
        #else
        if (isShaded) {
            //TODO: make branchless, infact apply ahead of time to the texture itself in ModelManager since that is
            // per face
            if ((face>>1) == 1) {//NORTH, SOUTH
                tinting.xyz *= 0.8f;
            } else if ((face>>1) == 2) {//EAST, WEST
                tinting.xyz *= 0.6f;
            } else if (face == 0) {//DOWN
                tinting.xyz *= 0.5f;
            }
        }
        #endif

        setTintingAndExtra(tinting, conditionalTinting, addin|(face<<8));
        #else
        interData.y = lighting|(face<<8);
        interData.z = tintColour;
        #endif
    }


    #ifdef DEBUG_RENDER
    quadDebug = lodLevel;
    #endif
}

#ifndef TAA_PATCH
vec2 taaShift() {return vec2(0.0);}
#endif