#version 450

#import <voxy:util/depthutils.glsl>

layout(location = 0) in vec2 uv;
layout(binding = 0) uniform sampler2D depthTex;
#ifdef OUTPUT_COLOUR
layout(location=0) out vec4 colour;
#endif
void main() {
    vec4 depths = textureGather(depthTex, uv, 0); // Get depth values from all surrounding texels.

    bvec4 cv = equal(vec4(FAR), depths);
    if (any(cv)) {//Patch holes (its very dodgy but should work :tm:, should clamp it to the first 3 levels)
        depths = mix(vec4(NEAR), depths, cv);
    }

    float res = REDUCTION(REDUCTION(depths.x, depths.y), REDUCTION(depths.z, depths.w));

    #ifdef OUTPUT_COLOUR
    colour = vec4(res);
    #else
    gl_FragDepth = res; // Write conservative depth.
    #endif
}
