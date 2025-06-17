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

uint extractDetail(SectionMeta section) {
    return section.posA>>28;
}

ivec3 extractPosition(SectionMeta section) {
    int y = ((int(section.posA)<<4)>>24);
    int x = (int(section.posB)<<4)>>8;
    int z = int((section.posA&((1u<<20)-1))<<4);
    z |= int(section.posB>>28);
    z <<= 8;
    z >>= 8;
    return ivec3(x,y,z);
}

uint extractQuadStart(SectionMeta meta) {
    return meta.ptr;
}

ivec3 extractAABBOffset(SectionMeta meta) {
    return (ivec3(meta.AABB)>>ivec3(0,5,10))&31;
}

ivec3 extractAABBSize(SectionMeta meta) {
    return ((ivec3(meta.AABB)>>ivec3(15,20,25))&31)+1;//The size is + 1 cause its always at least 1x1x1
}
