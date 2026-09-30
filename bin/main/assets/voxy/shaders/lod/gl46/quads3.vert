#version 460 core
#extension GL_ARB_gpu_shader_int64 : enable

#ifdef GL_ARB_gpu_shader_int64
#define QUAD_DATA_USE_64_BIT
#endif


#ifdef USE_NV_JANK
#extension GL_NV_gpu_shader5 : enable
#endif

#define QUAD_BUFFER_BINDING 1
#define MODEL_BUFFER_BINDING 3
#define MODEL_COLOUR_BUFFER_BINDING 4
#define POSITION_SCRATCH_BINDING 5
#define LIGHTING_SAMPLER_BINDING 1

#ifdef USE_SINGLE_TRI
#define USE_NV_BARRY
#endif

#import <voxy:lod/quad_format.glsl>
#import <voxy:lod/block_model.glsl>
#import <voxy:lod/gl46/bindings.glsl>
#import <voxy:lod/quad_util.glsl>

layout(location = 0) out flat uvec4 interData;
#ifndef USE_NV_BARRY
layout(location = 1) out vec2 uv;
#endif

#ifdef USE_NV_JANK
#ifdef GL_NV_gpu_shader5
out gl_PerVertex {
    f16vec4 gl_Position;
};
#endif
#endif

#ifdef DEBUG_RENDER
layout(location = 7) out flat uint quadDebug;
#endif

vec2 taaShift();

//TODO: add a mechanism so that some quads can ignore backface culling
// this would help alot with stuff like crops as they would look kinda weird i think,
// same with flowers etc
void main() {
    taaOffset = taaShift();

    QuadData quad;
    uvec2 pos = positionBuffer[gl_BaseInstance];
    setupQuad(quad, quadData[uint(gl_VertexID)>>2], pos, (gl_VertexID&3) == 1);

    uint cornerId = gl_VertexID&3;

    gl_Position =
    #ifdef USE_NV_JANK
    #ifdef GL_NV_gpu_shader5
    f16vec4
    #endif
    #endif
    (getQuadCornerPos(quad, cornerId));


    #ifndef USE_NV_BARRY
    uv = getCornerUV(quad, cornerId);
    #endif

    //Note: other data is automatically discarded as it is undefiend and has not been generated
    interData = quad.attributeData;


    #ifdef DEBUG_RENDER
    //quadDebug = uint(extractDetail(pos));
    quadDebug = uint(gl_VertexID)>>2;
    #endif
}

#ifndef TAA_PATCH
vec2 taaShift() {return vec2(0.0);}
#endif