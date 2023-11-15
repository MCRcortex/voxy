#version 460 core
#extension GL_ARB_gpu_shader_int64 : enable
#define VISIBILITY_ACCESS writeonly
#import <voxelmon:lod/gl46/bindings.glsl>
#import <voxelmon:lod/gl46/section.glsl>

flat out uint id;
flat out uint value;

void main() {
    SectionMeta section = sectionData[gl_VertexID>>3];

    uint detail = extractDetail(section);
    ivec3 ipos = extractPosition(section);

    //Transform ipos with respect to the vertex corner
    ipos += ivec3(gl_VertexID&1, (gl_VertexID>>1)&1, (gl_VertexID>>2)&1);

    vec3 cornerPos = vec3(((ipos<<detail)-baseSectionPos)<<5);
    gl_Position = MVP * vec4(cornerPos,1);

    //Write to this id
    id = gl_VertexID>>3;
    value = frameId;
}