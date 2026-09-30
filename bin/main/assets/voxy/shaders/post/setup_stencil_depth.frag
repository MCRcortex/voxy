#version 330 core

layout(binding = 0) uniform sampler2D depthTex;
layout(location = 1) uniform vec2 scaleFactor;

#import <voxy:util/depthutils.glsl>

in vec2 UV;
void main() {
    gl_FragDepth = NEAR;
    if (texture(depthTex, UV*scaleFactor).r==FAR) {
        discard;
    }
}