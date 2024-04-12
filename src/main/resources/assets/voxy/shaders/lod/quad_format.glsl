#line 1
#ifdef GL_ARB_gpu_shader_int64
#define Quad uint64_t

#define Eu32(data, amountBits, shift) (uint((data)>>(shift))&((1u<<(amountBits))-1))

vec3 extractPos(uint64_t quad) {
    //TODO: pull out the majic constants into #defines (specifically the shift amount)
    return vec3(Eu32(quad, 5, 21), Eu32(quad, 5, 16), Eu32(quad, 5, 11));
}

ivec2 extractSize(uint64_t quad) {
    return ivec2(Eu32(quad, 4, 3), Eu32(quad, 4, 7)) + ivec2(1);//the + 1 is cause you cant actually have a 0 size quad
}

uint extractFace(uint64_t quad) {
    return Eu32(quad, 3, 0);
}

uint extractStateId(uint64_t quad) {
    return Eu32(quad, 16, 26);
}

uint extractBiomeId(uint64_t quad) {
    return Eu32(quad, 9, 46);
}

uint extractLightId(uint64_t quad) {
    return Eu32(quad, 8, 55);
}

bool isQuadEmpty(uint64_t quad) {
    return quad == uint64_t(0);
}

#else
//TODO: FIXME, ivec2 swaps around the data of the x and y cause its written in little endian

#define Quad ivec2

//#define Eu32(data, amountBits, shift) (uint((data)>>(shift))&((1u<<(amountBits))-1))

uint Eu32v(ivec2 data, int amount, int shift) {
    if (shift > 31) {
        shift -= 32;
        return (uint(data.y)>>uint(shift))&((1u<<uint(amount))-1);
    } else {
        return (uint(data.x)>>uint(shift))&((1u<<uint(amount))-1);
    }
}

vec3 extractPos(ivec2 quad) {
    return vec3(Eu32v(quad, 5, 21), Eu32v(quad, 5, 16), Eu32v(quad, 5, 11));
}

ivec2 extractSize(ivec2 quad) {
    return ivec2(Eu32v(quad, 4, 3), Eu32v(quad, 4, 7)) + ivec2(1);//the + 1 is cause you cant actually have a 0 size quad
}

uint extractFace(ivec2 quad) {
    return Eu32v(quad, 3, 0);
}

uint extractStateId(ivec2 quad) {
    //Eu32(quad, 20, 26);
    return Eu32v(quad, 6, 26)|(Eu32v(quad, 14, 32)<<6);
}

uint extractBiomeId(ivec2 quad) {
    return Eu32v(quad, 9, 46);
}

uint extractLightId(ivec2 quad) {
    return Eu32v(quad, 8, 55);
}

bool isQuadEmpty(ivec2 quad) {
    return all(equal(quad, ivec2(0)));
}
#endif