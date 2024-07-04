#version 450


layout(location = 0) in vec2 uv;
layout(binding = 0) uniform sampler2D depthTex;
void main() {
    vec4 depths = textureGather(depthTex, uv, 0); // Get depth values from all surrounding texels.
    gl_FragDepth = max(max(depths.x, depths.y), max(depths.z, depths.w)); // Write conservative depth.
}