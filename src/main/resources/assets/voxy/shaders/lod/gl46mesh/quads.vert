#version 450
#extension GL_ARB_gpu_shader_int64 : enable

#define MESHLET_ACCESS readonly
#define QUADS_PER_MESHLET 126
//There are 16 bytes of metadata at the start of the meshlet
#define MESHLET_SIZE (QUADS_PER_MESHLET+2)
#import <voxy:lod/quad_format.glsl>
#import <voxy:lod/gl46mesh/bindings.glsl>
#define PosHeader Quad


#ifdef GL_ARB_gpu_shader_int64
ivec3 extractPosition(PosHeader pos64) {
    //((long)lvl<<60)|((long)(y&0xFF)<<52)|((long)(z&((1<<24)-1))<<28)|((long)(x&((1<<24)-1))<<4);
    //return ivec3((pos64<<4)&uint64_t(0xFFFFFFFF),(pos64>>28)&uint64_t(0xFFFFFFFF),(pos64>>24)&uint64_t(0xFFFFFFFF))>>ivec3(8,24,8);
    return (ivec3(int(pos64>>4)&((1<<24)-1), int(pos64>>52)&0xFF, int(pos64>>28)&((1<<24)-1))<<ivec3(8,24,8))>>ivec3(8,24,8);
}
uint extractDetail(PosHeader pos64) {
    return uint(pos64>>60);
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
#endif

PosHeader meshletPosition;
Quad quad;
bool setupMeshlet() {
    gl_CullDistance[0] = 1;
    //TODO: replace with vertexAttribute that has a divisor of 1
    uint data = meshlets[gl_InstanceID];
    if (data == uint(-1)) {//Came across a culled meshlet
        gl_CullDistance[0] = -1;
        //Since the primative is culled, dont need to do any more work or set any values as the primative is discarded
        // we dont need to care about undefined values
        return true;
    }

    uint baseId = (data*MESHLET_SIZE);
    uint quadIndex = baseId + (gl_VertexID>>2) + 2;
    meshletPosition = geometryPool[baseId];
    quad = geometryPool[quadIndex];
    if (isQuadEmpty(quad)) {
        gl_CullDistance[0] = -1;
        return true;
    }
    return false;
}

void main() {

    if (setupMeshlet()) {
        gl_Position = vec4(1.0f/0.0f);
        return;
    }
    if ((gl_VertexID>>2)!=0) {
        gl_Position = vec4(1.0f/0.0f);
        return;
    }

    uint detail = extractDetail(meshletPosition);
    ivec3 sectionPos = extractPosition(meshletPosition);

    ivec3 pos = (((sectionPos<<detail)-baseSectionPos)<<5);
    pos += ivec3(gl_VertexID&1, 0, (gl_VertexID>>1)&1)*(1<<detail);

    gl_Position = MVP * vec4(vec3(pos),1);
}