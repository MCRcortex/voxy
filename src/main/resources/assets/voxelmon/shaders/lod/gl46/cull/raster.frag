#version 460 core
#extension GL_ARB_gpu_shader_int64 : enable
#define VISIBILITY_ACCESS writeonly
#import <voxelmon:lod/gl46/bindings.glsl>
flat in uint id;
flat in uint value;

void main() {
    visibilityData[id] = value;
}