#version 450

layout(location = 1) in flat vec4 colour;
layout(location = 0) out vec4 outColour;
void main() {
    outColour = colour;
}