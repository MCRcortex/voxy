#version 460 core
#extension GL_ARB_gpu_shader_int64 : enable
#define VISIBILITY_ACCESS

#define SECTION_METADATA_BUFFER_BINDING 1
#define VISIBILITY_BUFFER_BINDING 2
#define INDIRECT_SECTION_LOOKUP_BINDING 3

#import <voxy:lod/gl46/bindings.glsl>
#import <voxy:lod/section.glsl>

flat out uint id;
flat out uint value;

void main() {
    uint sid = indirectLookup[gl_InstanceID];

    SectionMeta section = sectionData[sid];

    uint detail = extractDetail(section);
    ivec3 ipos = extractPosition(section);
    ivec3 aabbOffset = extractAABBOffset(section);
    ivec3 size = extractAABBSize(section);

    //Transform ipos with respect to the vertex corner
    ivec3 pos = (((ipos<<detail)-baseSectionPos)<<5);
    pos += (aabbOffset-1)*(1<<detail);
    pos += (ivec3(gl_VertexID&1, (gl_VertexID>>2)&1, (gl_VertexID>>1)&1)*(size+2))*(1<<detail);

    gl_Position = MVP * vec4(vec3(pos),1);

    //Write to the section id, to track temporal over time (litterally just need a single bit, 1 fking bit, but no)
    id = sid;

    uint previous = visibilityData[sid]&0x7fffffffu;
    bool wasVisibleLastFrame = previous==(frameId-1);
    value = (frameId&0x7fffffffu)|(uint(wasVisibleLastFrame)<<31);//Encode if it was visible last frame
}