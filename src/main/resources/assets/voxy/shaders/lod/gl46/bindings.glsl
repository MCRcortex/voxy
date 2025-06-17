#line 1

layout(binding = 0, std140) uniform SceneUniform {
    mat4 MVP;
    ivec3 baseSectionPos;
    uint frameId;
    vec3 cameraSubPos;
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
    uint temporalOpaqueDrawCount;

    DrawCommand cullDrawIndirectCommand;
};
#endif

#ifdef SECTION_METADATA_BUFFER_BINDING
layout(binding = SECTION_METADATA_BUFFER_BINDING, std430) readonly restrict buffer SectionBuffer {
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

#ifdef POSITION_SCRATCH_BINDING
#ifndef POSITION_SCRATCH_ACCESS
#define POSITION_SCRATCH_ACCESS readonly
#endif
layout(binding = POSITION_SCRATCH_BINDING, std430) POSITION_SCRATCH_ACCESS restrict buffer PositionScratchBuffer {
    uvec2 positionBuffer[];
};
#endif

#ifdef LIGHTING_SAMPLER_BINDING

layout(binding = LIGHTING_SAMPLER_BINDING) uniform sampler2D lightSampler;

vec4 getLighting(uint index) {
    int i2 = int(index);
    return texture(lightSampler, clamp((vec2((i2>>4)&0xF, i2&0xF))/16, vec2(8.0f/255), vec2(248.0f/255)));
}
#endif

