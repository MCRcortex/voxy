#version 460 core
layout(location = 0) in flat vec4 colour;
layout(location = 0) out vec4 outColour;
void main() {
    //TODO: randomly discard the fragment with respect to the alpha value

    outColour = colour;
}