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
    return section.a.x>>28;
}

ivec3 extractPosition(SectionMeta section) {
    int y = ((int(section.a.x)<<4)>>24);
    int x = (int(section.a.y)<<4)>>8;
    int z = int((section.a.x&((1u<<20)-1))<<4);
    z |= int(section.a.y>>28);
    z <<= 8;
    z >>= 8;
    return ivec3(x,y,z);
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
