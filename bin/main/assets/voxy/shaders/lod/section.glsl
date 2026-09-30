#import <voxy:lod/pos_util.glsl>
/*
struct SectionMeta {
    uint posA;
    uint posB;
    uint AABB;
    uint ptr;
    uint cntA;
    uint cntB;
    uint cntC;
    uint cntD;
};
*/
struct SectionMeta {
    uvec4 a;
    uvec4 b;
};

uvec2 extractRawPos(SectionMeta section) {
    return section.a.xy;
}

uint extractDetail(SectionMeta section) {
    return getLoDLevel(section.a.xy);
}

ivec3 extractPosition(SectionMeta section) {
    return getLoDPosition(section.a.xy);
}

uint extractQuadStart(SectionMeta meta) {
    return meta.a.w;
}

ivec3 extractAABBOffset(SectionMeta meta) {
    return (ivec3(meta.a.z)>>ivec3(0,5,10))&31;
}

ivec3 extractAABBSize(SectionMeta meta) {
    return ((ivec3(meta.a.z)>>ivec3(15,20,25))&31)+1;//The size is + 1 cause its always at least 1x1x1
}
