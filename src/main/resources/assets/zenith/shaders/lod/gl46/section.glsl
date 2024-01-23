uint extractDetail(SectionMeta section) {
    return section.header.x>>28;
}

ivec3 extractPosition(SectionMeta section) {
    int y = ((int(section.header.x)<<4)>>24);
    int x = (int(section.header.y)<<4)>>8;
    int z = int((section.header.x&((1<<20)-1))<<4);
    z |= int(section.header.y>>28);
    z <<= 8;
    z >>= 8;
    return ivec3(x,y,z);
}

uint extractQuadStart(SectionMeta meta) {
    return meta.drawdata.x;
}

uint extractQuadCount(SectionMeta meta) {
    return meta.drawdata.y;
}
