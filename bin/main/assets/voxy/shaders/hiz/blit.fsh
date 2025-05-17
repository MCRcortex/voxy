#version 450


layout(location = 0) in vec2 uv;
layout(binding = 0) uniform sampler2D depthTex;
void main() {
    vec4 depths = textureGather(depthTex, uv, 0); // Get depth values from all surrounding texels.

    bvec4 cv = lessThanEqual(vec4(0.999999f), depths);
    if (any(cv)) {//Patch holes (its very dodgy but should work :tm:, should clamp it to the first 3 levels)
        depths = mix(vec4(0.0f), depths, cv);
    }

    gl_FragDepth = max(max(depths.x, depths.y), max(depths.z, depths.w)); // Write conservative depth.
}
