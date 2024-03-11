struct SectionMeta {
    uint posA;
    uint posB;
    uint AABB;
    uint ptr;
    uint cntA;
    uint cntB;
    uint cntC;
    uint cntD;
};

struct BlockModel {
    uint faceData[6];
    uint flagsA;
    uint colourTint;
    uint _pad[8];
};


layout(binding = 0) uniform sampler2D blockModelAtlas;

layout(binding = 0, std140) uniform SceneUniform {
    mat4 MVP;
    ivec3 baseSectionPos;
    int sectionCount;
    vec3 cameraSubPos;
    uint frameId;
};

#define Quad uint64_t
layout(binding = 1, std430) readonly restrict buffer QuadBuffer {
    Quad quadData[];
};

layout(binding = 2, std430) readonly restrict buffer SectionBuffer {
    SectionMeta sectionData[];
};

#ifndef VISIBILITY_ACCESS
#define VISIBILITY_ACCESS readonly
#endif
layout(binding = 3, std430) VISIBILITY_ACCESS restrict buffer VisibilityBuffer {
    uint visibilityData[];
};

layout(binding = 4, std430) readonly restrict buffer ModelBuffer {
    BlockModel modelData[];
};

layout(binding = 5, std430) readonly restrict buffer ModelColourBuffer {
    uint colourData[];
};

layout(binding = 6, std430) readonly restrict buffer LightingBuffer {
    uint lightData[];
};

vec4 getLighting(uint index) {
    uvec4 arr = uvec4(lightData[index]);
    arr = arr>>uvec4(16,8,0,24);
    arr = arr & uvec4(0xFF);
    return vec4(arr)*vec4(1.0f/255.0f);
}

