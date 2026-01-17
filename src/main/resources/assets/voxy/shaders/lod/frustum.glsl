struct Frustum {
    vec4 planes[6];
};

// Optimized frustum test that precomputes the base dot product and size contribution
// For each plane, we compute: dot(plane.xyz, base) + size * dot(plane.xyz, selectMask) >= -plane.w
// where selectMask chooses the corner furthest in the direction of the plane normal
// This avoids the mix() and lessThan() per plane by precomputing a size multiplier

bool testPlaneOptimized(vec4 plane, float baseDot, float sizeMultiplier) {
    return baseDot + sizeMultiplier >= -plane.w;
}

bool outsideFrustum(in Frustum frustum, vec3 pos, float size) {
    // For each plane, compute the positive extent contribution
    // When plane.xyz component is positive, we add size to that axis to get furthest corner
    // The dot product of plane.xyz with vec3(size) where positive components = size, negative = 0
    // equals size * (max(plane.x, 0) + max(plane.y, 0) + max(plane.z, 0))
    // which equals size * dot(max(plane.xyz, 0), vec3(1))

    // Precompute base dot products for all planes
    float baseDot0 = dot(frustum.planes[0].xyz, pos);
    float baseDot1 = dot(frustum.planes[1].xyz, pos);
    float baseDot2 = dot(frustum.planes[2].xyz, pos);
    float baseDot3 = dot(frustum.planes[3].xyz, pos);
    float baseDot4 = dot(frustum.planes[4].xyz, pos);

    // Precompute size multipliers: size * sum of positive normal components
    float sizeMult0 = size * dot(max(frustum.planes[0].xyz, vec3(0)), vec3(1));
    float sizeMult1 = size * dot(max(frustum.planes[1].xyz, vec3(0)), vec3(1));
    float sizeMult2 = size * dot(max(frustum.planes[2].xyz, vec3(0)), vec3(1));
    float sizeMult3 = size * dot(max(frustum.planes[3].xyz, vec3(0)), vec3(1));
    float sizeMult4 = size * dot(max(frustum.planes[4].xyz, vec3(0)), vec3(1));

    // Test all planes - if any fails, the AABB is outside
    return !((baseDot0 + sizeMult0 >= -frustum.planes[0].w) &&
             (baseDot1 + sizeMult1 >= -frustum.planes[1].w) &&
             (baseDot2 + sizeMult2 >= -frustum.planes[2].w) &&
             (baseDot3 + sizeMult3 >= -frustum.planes[3].w) &&
             (baseDot4 + sizeMult4 >= -frustum.planes[4].w));
}