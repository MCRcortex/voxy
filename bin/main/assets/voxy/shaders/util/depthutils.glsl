//These defines are definatly going to break some shaders somehow

#ifndef UNDEFINE_DEPTH
#define UNDEFINE_DEPTH

#ifdef USE_REVERSE_Z
#define REDUCTION min
#define REDUCTION2 max
#define NEAR 1.0f
#define FAR 0.0f
#define CLOSER_SIGN 1.0f
#define DEPTH_SCALAR_COMPARE(a,b) ((a)>(b))
#define DEPTH_SCALAR_COMPARE_EQUAL(a,b) ((a)>=(b))
#else
#define REDUCTION max
#define REDUCTION2 min
#define NEAR 0.0f
#define FAR 1.0f
#define CLOSER_SIGN -1.0f
#define DEPTH_SCALAR_COMPARE(a,b) ((a)<(b))
#define DEPTH_SCALAR_COMPARE_EQUAL(a,b) ((a)<=(b))
#endif



#ifdef USE_ZERO_ONE_DEPTH
vec3 NDC2SCREEN(vec3 val) {
    return vec3(val.xy*0.5f+0.5f, val.z);
}
vec3 SCREEN2NDC(vec3 val) {
    return vec3(val.xy*2.0f-1.0f, val.z);
}
float NDC2SCREEN_DEPTH(float val) {
    return val;
}
float SCREEN2NDC_DEPTH(float val) {
    return val;
}
#else
vec3 NDC2SCREEN(vec3 val) {
    return val*0.5f+0.5f;
}
vec3 SCREEN2NDC(vec3 val) {
    return val*2.0f-1.0f;
}
float NDC2SCREEN_DEPTH(float val) {
    return val*0.5f+0.5f;
}
float SCREEN2NDC_DEPTH(float val) {
    return val*2.0f-1.0f;
}
#endif


#else
#undef UNDEFINE_DEPTH

#undef REDUCTION
#undef REDUCTION2
#undef NEAR
#undef FAR
#undef CLOSER_SIGN
#undef DEPTH_SCALAR_COMPARE
#undef DEPTH_SCALAR_COMPARE_EQUAL
#undef USE_ZERO_ONE_DEPTH

#endif