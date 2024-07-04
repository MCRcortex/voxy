#define extractMeshletStart extractQuadStart
#define PosHeader Quad
#define AABBHeader Quad

//There are 16 bytes of metadata at the start of the meshlet
#define MESHLET_SIZE (QUADS_PER_MESHLET+2)

#ifdef GL_ARB_gpu_shader_int64
ivec3 extractPosition(PosHeader pos64) {
    //((long)lvl<<60)|((long)(y&0xFF)<<52)|((long)(z&((1<<24)-1))<<28)|((long)(x&((1<<24)-1))<<4);
    //return ivec3((pos64<<4)&uint64_t(0xFFFFFFFF),(pos64>>28)&uint64_t(0xFFFFFFFF),(pos64>>24)&uint64_t(0xFFFFFFFF))>>ivec3(8,24,8);
    return (ivec3(int(pos64>>4)&((1<<24)-1), int(pos64>>52)&0xFF, int(pos64>>28)&((1<<24)-1))<<ivec3(8,24,8))>>ivec3(8,24,8);
}
uint extractDetail(PosHeader pos64) {
    return uint(pos64>>60);
}
uvec3 extractMin(AABBHeader aabb) {
    return uvec3(uint(uint(aabb)&0xFF),uint((uint(aabb)>>8)&0xFF),uint((uint(aabb)>>16)&0xFF));
}
uvec3 extractMax(AABBHeader aabb) {
    return uvec3(uint((aabb>>24)&0xFF),uint((aabb>>32)&0xFF),uint((aabb>>40)&0xFF));
}
#else
ivec3 extractPosition(PosHeader pos) {
    int y = ((int(pos.x)<<4)>>24);
    int x = (int(pos.y)<<4)>>8;
    int z = int((pos.x&((1<<20)-1))<<4);
    z |= int(pos.y>>28)&0xF;
    z <<= 8;
    z >>= 8;
    return ivec3(x,y,z);
}

uint extractDetail(PosHeader pos) {
    return uint(pos.x)>>28;
}

uvec3 extractMin(AABBHeader aabb) {
    return uvec3(aabb.x&0xFF,(aabb.x>>8)&0xFF,(aabb.x>>16)&0xFF);
}
uvec3 extractMax(AABBHeader aabb) {
    return uvec3((aabb.x>>24)&0xFF,aabb.y&0xFF,(aabb.y>>8)&0xFF);
}
#endif