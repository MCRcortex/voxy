#version 450 core

layout(binding = 0) uniform sampler2D colourTex;
layout(binding = 1) uniform sampler2D depthTex;
layout(location = 2) uniform mat4 invProjMat;
layout(location = 3) uniform mat4 projMat;

out vec4 colour;
in vec2 UV;

vec3 rev3d(vec3 clip) {
    vec4 view = invProjMat * vec4(clip*2.0f-1.0f,1.0f);
    return view.xyz/view.w;
}
float projDepth(vec3 pos) {
    vec4 view = projMat * vec4(pos, 1);
    return view.z/view.w;
}

void main() {
    colour = texture(colourTex, UV.xy);
    if (colour.a == 0.0) {
        discard;
    }

    float depth = texture(depthTex, UV.xy).r;
    if (depth == 0.0f) {
        discard;
    }

    depth = projDepth(rev3d(vec3(UV.xy, depth)));
    depth = min(1.0f-(2.0f/((1<<24)-1)), depth);
    depth = depth * 0.5f + 0.5f;
    depth = gl_DepthRange.diff * depth + gl_DepthRange.near;
    gl_FragDepth = depth;
}