#version 330 core
out vec4 colour;
in vec2 UV;
#import <voxy:util/depthutils.glsl>
void main() {
    colour = vec4(1,0,1,1);
    gl_FragDepth = NEAR;
}