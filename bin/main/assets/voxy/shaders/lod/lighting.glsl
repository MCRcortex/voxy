#ifndef _VOXY_LIGHTING_DECL
#define _VOXY_LIGHTING_DECL

vec2 getLightmapUv(uint index) {
    vec2 base = vec2((index>>4)&0xFu, index&0xFu)/15;

    return clamp(base*(15.0f/16.0f)+(0.5/16.0f), vec2(8.0f/256), vec2(248.0f/256));//+vec2(8.0f/256)
}

#ifdef LIGHTING_SAMPLER_BINDING

layout(binding = LIGHTING_SAMPLER_BINDING) uniform sampler2D lightSampler;

vec4 getLighting(uint index) {
    return textureLod(lightSampler, getLightmapUv(index), 0);
}
#endif

#endif