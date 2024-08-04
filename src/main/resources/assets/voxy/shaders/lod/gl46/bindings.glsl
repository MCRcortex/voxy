#line 1

layout(binding = 0, std140) uniform SceneUniform {
    mat4 MVP;
    ivec3 baseSectionPos;
    uint frameId;
    vec3 cameraSubPos;
};

struct BlockModel {
    uint faceData[6];
    uint flagsA;
    uint colourTint;
    uint _pad[8];
};

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

//TODO: see if making the stride 2*4*4 bytes or something cause you get that 16 byte write
struct DrawCommand {
    uint  count;
    uint  instanceCount;
    uint  firstIndex;
    int  baseVertex;
    uint  baseInstance;
};


#ifdef BLOCK_MODEL_TEXTURE_BINDING
layout(binding = BLOCK_MODEL_TEXTURE_BINDING) uniform sampler2D blockModelAtlas;
#endif


#ifndef Quad
#define Quad ivec2
#endif
#ifdef QUAD_BUFFER_BINDING
layout(binding = QUAD_BUFFER_BINDING, std430) readonly restrict buffer QuadBuffer {
    Quad quadData[];
};
#endif

#ifdef DRAW_BUFFER_BINDING
layout(binding = DRAW_BUFFER_BINDING, std430) writeonly restrict buffer DrawBuffer {
    DrawCommand cmdBuffer[];
};
#endif

#ifdef DRAW_COUNT_BUFFER_BINDING
layout(binding = DRAW_COUNT_BUFFER_BINDING, std430) restrict buffer DrawCommandCountBuffer {
    uint cmdGenDispatchX;
    uint cmdGenDispatchY;
    uint cmdGenDispatchZ;
    uint opaqueDrawCount;
    uint translucentDrawCount;
};
#endif

#ifdef SECTION_METADA_BUFFER_BINDING
layout(binding = SECTION_METADA_BUFFER_BINDING, std430) readonly restrict buffer SectionBuffer {
    SectionMeta sectionData[];
};
#endif

#ifdef INDIRECT_SECTION_LOOKUP_BINDING
layout(binding = INDIRECT_SECTION_LOOKUP_BINDING, std430) readonly restrict buffer IndirectSectionLookupBuffer {
    uint sectionCount;
    uint indirectLookup[];
};
#endif

#ifndef VISIBILITY_ACCESS
#define VISIBILITY_ACCESS readonly
#endif
#ifdef VISIBILITY_BUFFER_BINDING
layout(binding = VISIBILITY_BUFFER_BINDING, std430) VISIBILITY_ACCESS restrict buffer VisibilityBuffer {
    uint visibilityData[];
};
#endif

#ifdef MODEL_BUFFER_BINDING
layout(binding = MODEL_BUFFER_BINDING, std430) readonly restrict buffer ModelBuffer {
    BlockModel modelData[];
};
#endif

#ifdef MODEL_COLOUR_BUFFER_BINDING
layout(binding = MODEL_COLOUR_BUFFER_BINDING, std430) readonly restrict buffer ModelColourBuffer {
    uint colourData[];
};
#endif

#ifdef LIGHTING_BUFFER_BINDING
layout(binding = LIGHTING_BUFFER_BINDING, std430) readonly restrict buffer LightingBuffer {
    uint lightData[];
};

vec4 getLighting(uint index) {
    uvec4 arr = uvec4(lightData[index]);
    arr = arr>>uvec4(16,8,0,24);
    arr = arr & uvec4(0xFF);
    return vec4(arr)*vec4(1.0f/255.0f);
}
#endif

