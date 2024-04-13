#version 460 core
#extension GL_ARB_gpu_shader_int64 : enable
#define VISIBILITY_ACCESS writeonly
#import <voxy:lod/gl46mesh/bindings.glsl>
#import <voxy:lod/section.glsl>

flat out uint id;
flat out uint value;

void main() {
    uint sid = gl_InstanceID;

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

    //Write to this id
    id = sid;
    value = frameId;
}