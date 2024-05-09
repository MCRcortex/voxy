#version 460 core
#define VISIBILITY_ACCESS writeonly
#import <voxy:lod/gl46mesh/bindings.glsl>
layout(early_fragment_tests) in;

flat in uint id;
flat in uint value;
out vec4 colour;

void main() {
    visibilityData[id] = value;
    colour = vec4(float(id&7u)/7, float((id>>3)&7u)/7, float((id>>6)&7u)/7, 1);
}