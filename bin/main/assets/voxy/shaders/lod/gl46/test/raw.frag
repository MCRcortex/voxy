#version 460 core

layout(location = 6) in flat uint quadDebug;
layout(location = 0) out vec4 outColour;
void main() {
    outColour = vec4(float(quadDebug&15u)/15, float((quadDebug>>4)&15u)/15, float((quadDebug>>8)&15u)/15, 1);
}