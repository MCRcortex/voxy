#version 450


layout(location = 0) in vec2 uv;
layout(binding = 0) uniform sampler2D depthTex;
#ifdef OUTPUT_COLOUR
layout(location=0) out vec4 colour;
#endif
void main() {
    // textureGather can be buggy on some drivers (RDNA1?).
    // We manually fetch the 4 texels (2x2 reduction) using texelFetch for absolute control.
    ivec2 basePos = ivec2(gl_FragCoord.xy) * 2;
    vec4 depths;
    depths.x = texelFetch(depthTex, basePos + ivec2(0,0), 0).r;
    depths.y = texelFetch(depthTex, basePos + ivec2(1,0), 0).r;
    depths.z = texelFetch(depthTex, basePos + ivec2(0,1), 0).r;
    depths.w = texelFetch(depthTex, basePos + ivec2(1,1), 0).r;

    bvec4 cv = lessThanEqual(vec4(0.999999999f), depths);
    if (any(cv)) {//Patch holes (its very dodgy but should work :tm:, should clamp it to the first 3 levels)
        depths = mix(vec4(0.0f), depths, cv);
    }
    // Standard max-reduction
    float res = max(max(depths.x, depths.y), max(depths.z, depths.w));

    #ifdef OUTPUT_COLOUR
    colour = vec4(res);
    #else
    gl_FragDepth = res; // Write conservative depth.
    #endif
}
