#version 460 core
#extension GL_ARB_gpu_shader_int64 : enable
#define VISIBILITY_ACCESS writeonly
#import <voxelmon:lod/gl46/bindings.glsl>
flat in uint id;
flat in uint value;
//out vec4 colour;

void main() {
    visibilityData[id] = value;
    //colour = vec4(float(id&7)/7, float((id>>3)&7)/7, float((id>>6)&7)/7, 1);
}