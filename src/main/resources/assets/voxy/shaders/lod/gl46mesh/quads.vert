#version 450
#extension GL_ARB_gpu_shader_int64 : enable
#extension GL_ARB_shader_draw_parameters : require

#import <voxy:lod/quad_format.glsl>
#import <voxy:lod/gl46mesh/bindings.glsl>
#import <voxy:lod/block_model.glsl>
#import <voxy:lod/gl46mesh/meshlet.glsl>

layout(location = 6) out flat uint meshlet;
PosHeader meshletPosition;
Quad quad;
bool setupMeshlet() {
    gl_CullDistance[0] = 1;
    //TODO: replace with vertexAttribute that has a divisor of 1
    uint data = meshlets[gl_InstanceID + gl_BaseInstanceARB];
    if (data == uint(-1)) {//Came across a culled meshlet
        gl_CullDistance[0] = -1;
        //Since the primative is culled, dont need to do any more work or set any values as the primative is discarded
        // we dont need to care about undefined values
        return true;
    }

    meshlet = data;
    uint baseId = (data*MESHLET_SIZE);
    uint quadIndex = baseId + (gl_VertexID>>2) + 2;
    meshletPosition = geometryPool[baseId];
    quad = geometryPool[quadIndex];
    if (isQuadEmpty(quad)) {
        gl_CullDistance[0] = -1;
        return true;
    }
    return false;
}




layout(location = 0) out vec2 uv;
layout(location = 1) out flat vec2 baseUV;
layout(location = 2) out flat vec4 tinting;
layout(location = 3) out flat vec4 addin;
layout(location = 4) out flat uint flags;
layout(location = 5) out flat vec4 conditionalTinting;

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

void main() {

    if (setupMeshlet()) {
        gl_Position = vec4(1.0f/0.0f);
        return;
    }

    uint lodLevel = extractDetail(meshletPosition);
    ivec3 sectionPos = extractPosition(meshletPosition);

    //meshlet = (meshlet<<5)|(gl_VertexID>>2);



    int cornerIdx = gl_VertexID&3;
    vec3 innerPos = extractPos(quad);
    uint face = extractFace(quad);
    uint modelId = extractStateId(quad);
    BlockModel model = modelData[modelId];
    uint faceData = model.faceData[face];
    bool isTranslucent = modelIsTranslucent(model);
    bool hasAO = modelHasMipmaps(model);//TODO: replace with per face AO flag
    bool isShaded = hasAO;//TODO: make this a per face flag


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

    vec3 origin = vec3(((sectionPos<<lodLevel)-baseSectionPos)<<5);
    gl_Position = MVP*vec4((cornerPos+swizzelDataAxis(face>>1,vec3(cQuadSize,0)))*(1<<lodLevel)+origin, 1.0);
}