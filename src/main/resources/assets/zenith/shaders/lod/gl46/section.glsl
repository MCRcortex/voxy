uint extractDetail(SectionMeta section) {
    return section.posA>>28;
}

ivec3 extractPosition(SectionMeta section) {
    int y = ((int(section.posA)<<4)>>24);
    int x = (int(section.posB)<<4)>>8;
    int z = int((section.posA&((1<<20)-1))<<4);
    z |= int(section.posB>>28);
    z <<= 8;
    z >>= 8;
    return ivec3(x,y,z);
}

uint extractQuadStart(SectionMeta meta) {
    return meta.ptr;
}

uint extractQuadCount(SectionMeta meta) {
    return meta.cnt;
}
