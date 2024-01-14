#version 460 core
#extension GL_ARB_gpu_shader_int64 : enable

#import <voxelmon:lod/gl46/quad_format.glsl>
#import <voxelmon:lod/gl46/bindings.glsl>

layout(location = 0) out flat vec4 colour;

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

void main() {
    int cornerIdx = gl_VertexID&3;

    Quad quad = quadData[uint(gl_VertexID)>>2];
    vec3 innerPos = extractPos(quad);

    uint face = extractFace(quad);

    uint lodLevel = extractLodLevel();
    ivec3 lodCorner = ((extractRelativeLodPos()<<lodLevel) - (baseSectionPos&(ivec3((1<<lodLevel)-1))))<<5;
    vec3 corner = innerPos * (1<<lodLevel) + lodCorner;

    //TODO: see if backface culling is even needed, since everything (should) be back culled already
    //Flip the quad rotation by its face (backface culling)
    if ((face&1) != 0) {
        cornerIdx ^= 1;
    }
    if ((face>>1) == 0) {
        cornerIdx ^= 1;
    }

    ivec2 size = extractSize(quad) * ivec2((cornerIdx>>1)&1, cornerIdx&1) * (1<<lodLevel);

    vec3 pos = corner;

    //NOTE: can just make instead of face, make it axis (can also make it 2 bit instead of 3 bit then)
    // since the only reason face is needed is to ensure backface culling orientation thing
    uint axis = face>>1;
    if (axis == 0) {
        pos.xz += size;
        pos.y += (face&1)<<lodLevel;
    } else if (axis == 1) {
        pos.xy += size;
        pos.z += (face&1)<<lodLevel;
    } else {
        pos.yz += size;
        pos.x += (face&1)<<lodLevel;
    }

    gl_Position = MVP * vec4(pos,1);

    uint stateId = extractStateId(quad);
    uint biomeId = extractBiomeId(quad);
    State stateInfo = stateData[stateId];
    colour = uint2vec4RGBA(stateInfo.faceColours[face]);

    colour *= getLighting(extractLightId(quad));

    if (((stateInfo.biomeTintMsk>>face)&1) == 1) {
        vec4 biomeColour = uint2vec4RGBA(biomeData[biomeId].foliage);
        colour *= biomeColour;
    }
    //Apply water tint
    if (((stateInfo.biomeTintMsk>>6)&1) == 1) {
        colour *= vec4(0.247, 0.463, 0.894, 1);
    }

    //Apply face tint
    if (face == 0) {
        colour.xyz *= vec3(0.75, 0.75, 0.75);
    } else if (face != 1) {
        colour.xyz *= vec3((float(face-2)/4)*0.5 + 0.5);
    }


}
//gl_Position = MVP * vec4(vec3(((cornerIdx)&1)+10,10,((cornerIdx>>1)&1)+10),1);
//uint i = uint(quad>>32);
//uint i = uint(gl_BaseInstance);
//i ^= i>>16;
//i *= 124128573;
//i ^= i>>16;
//i *= 4211346123;
//i ^= i>>16;
//i *= 462312435;
//i ^= i>>16;
//i *= 542354341;
//i ^= i>>16;

//uint i = uint(lodLevel+12)*215387625;
//colour *= vec4(vec3(float((uint(i)>>2)&7)/7,float((uint(i)>>5)&7)/7,float((uint(i)>>8)&7)/7)*0.7+0.3,1);
//colour = vec4(vec3(float((uint(i)>>2)&7)/7,float((uint(i)>>5)&7)/7,float((uint(i)>>8)&7)/7),1);