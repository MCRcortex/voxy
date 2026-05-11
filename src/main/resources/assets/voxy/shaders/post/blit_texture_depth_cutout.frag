#version 430 core

layout(binding = 0) uniform sampler2D depthTex;

// M9 migration: location-based uniforms wrapped in two UBO push blocks so
// the shader compiles on Metal/Vulkan. Split into MATS + FOG so each
// caller (transformBlitDepth vs. NormalRenderPipeline.finish) can write
// its own struct independently without trampling the other's data on GL.
#ifndef PUSH_MATS_BINDING
#define PUSH_MATS_BINDING 14
#endif
layout(binding = PUSH_MATS_BINDING, std140) uniform PushMats {
    mat4 invProjMat;
    mat4 projMat;
};

#ifdef EMIT_COLOUR
layout(binding = 3) uniform sampler2D colourTex;
#ifdef USE_ENV_FOG
#ifndef PUSH_FOG_BINDING
#define PUSH_FOG_BINDING 15
#endif
layout(binding = PUSH_FOG_BINDING, std140) uniform PushFog {
    vec4 endParams;
    vec4 fogColour;
};
#endif
#endif

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
    float depth = texture(depthTex, UV.xy).r;
    if (depth == 0.0f || depth == 1.0) {
        discard;
    }

    vec3 point = rev3d(vec3(UV.xy, depth));
    depth = projDepth(point);
    depth = min(1.0f-(2.0f/((1<<24)-1)), depth);
    depth = depth * 0.5f + 0.5f;
    // M9 Phase 2 patch: gl_DepthRange isn't reliably exposed by glslang/shaderc
    // in the fragment stage, which broke SPIRV/MSL compilation for this shader.
    // Voxy never calls glDepthRange() to set a non-default range, so the
    // original math (gl_DepthRange.diff * depth + gl_DepthRange.near) reduces
    // to (1.0 * depth + 0.0) = depth. If a caller ever needs a custom depth
    // range, plumb it through a UBO instead of relying on the built-in block.
    #ifdef VOXY_VULKAN
    // no-op — assume default depth range [0,1]
    #else
    depth = gl_DepthRange.diff * depth + gl_DepthRange.near;
    #endif
    gl_FragDepth = depth;

    #ifdef EMIT_COLOUR
    colour = texture(colourTex, UV.xy);
    if (colour.a == 0.0) {
        discard;
    }
    #ifdef USE_ENV_FOG
    if (fogColour.a>0.0){
        float fogLerp = clamp(fma(length(point.xyz),endParams.x,endParams.y),0,endParams.z);//512 is 32*16 which is the render distance in blocks
        colour.rgb = mix(colour.rgb, fogColour.rgb, fogLerp*fogColour.a);
    }
    #endif
    #else
    colour = vec4(0);
    #endif

}
