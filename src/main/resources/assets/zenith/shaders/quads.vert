#version 460 core

layout(location = 0) out flat vec4 colour;
layout(binding = 0, std140) uniform SceneUniform {
    mat4 MVP;
    ivec3 baseSectionPos;
    int _pad1;
};

void main() {
    int cornerIdx = gl_VertexID&3;



    gl_Position = MVP * vec4(vec3(cornerIdx&1,((cornerIdx>>1)&1),0),1);

    colour = vec4(float((uint(gl_VertexID)>>2)&7)/7,float((uint(gl_VertexID)>>5)&7)/7,float((uint(gl_VertexID)>>8)&7)/7,1);
}