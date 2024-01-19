bool testFrustumPoint(vec4 plane, vec3 min, vec3 max) {
    vec3 point = mix(max, min, lessThan(plane.xyz, vec3(0))) * plane.xyz;
    return (point.x + point.y + point.z) >= -plane.w;
}

bool testFrustum(Frustum frust, vec3 min, vec3 max) {
    return  testFrustumPoint(frust.planes[0], min, max) &&
    testFrustumPoint(frust.planes[1], min, max) &&
    testFrustumPoint(frust.planes[2], min, max) &&
    testFrustumPoint(frust.planes[3], min, max) &&
    testFrustumPoint(frust.planes[4], min, max) &&
    testFrustumPoint(frust.planes[5], min, max);
}