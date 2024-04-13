#version 450
#extension GL_ARB_gpu_shader_int64 : enable

#define MESHLET_ACCESS readonly
#define QUADS_PER_MESHLET 126
//There are 16 bytes of metadata at the start of the meshlet
#define MESHLET_SIZE (QUADS_PER_MESHLET+2)
#import <voxy:lod/quad_format.glsl>
#import <voxy:lod/gl46mesh/bindings.glsl>
#import <voxy:lod/section.glsl>
#define GEOMETRY_FMT Quad

GEOMETRY_FMT meshletPosition;
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

    uint baseId = (data*QUADS_PER_MESHLET);
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
        return;
    }
}