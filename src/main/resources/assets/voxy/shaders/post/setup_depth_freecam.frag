#version 450 core

layout(binding = 0) uniform sampler2D vanillaDepthTex;
layout(location = 1) uniform mat4 inverseVanillaMVP;
layout(location = 2) uniform mat4 voxyMVP;
layout(location = 3) uniform vec2 scaleFactor;

#import <voxy:util/depthutils.glsl>

in vec2 UV;

const float VANILLA_DEPTH_PRIORITY = 0.125f;

vec3 unprojectVanillaDepth(vec3 screenPosition) {
    vec4 cameraRelativePosition = inverseVanillaMVP * vec4(SCREEN2NDC(screenPosition), 1.0f);
    return cameraRelativePosition.xyz / cameraRelativePosition.w;
}

void main() {
    float depth = texture(vanillaDepthTex, UV * scaleFactor).r;
    if (depth == FAR) {
        discard;
    }

    vec3 cameraRelativePosition = unprojectVanillaDepth(vec3(UV, depth));
    float distanceToCamera = length(cameraRelativePosition);
    cameraRelativePosition *= max(distanceToCamera - VANILLA_DEPTH_PRIORITY, 0.0f) / max(distanceToCamera, 0.0001f);
    vec4 voxyClipPosition = voxyMVP * vec4(cameraRelativePosition, 1.0f);
    float voxyDepth = NDC2SCREEN_DEPTH(voxyClipPosition.z / voxyClipPosition.w);
    gl_FragDepth = clamp(gl_DepthRange.diff * voxyDepth + gl_DepthRange.near, 0.0f, 1.0f);
}
