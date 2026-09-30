#version 460

#import <voxy:util/depthutils.glsl>

layout(binding = 0, std140) uniform SceneUniform {
    mat4 MVP;
    ivec4 cameraBlockPos;
    vec4 negInnerBlock;
};

layout(binding = 1, std430) restrict readonly buffer ChunkPosBuffer {
    ivec2[] chunkPos;
};

ivec3 unpackPos(ivec2 pos) {
    return ivec3(pos.y>>10, (pos.x<<12)>>12, ((pos.y<<22)|int(uint(pos.x)>>10))>>10);
}

bool shouldRender(ivec3 icorner) {
    vec3 corner = vec3(mix(mix(ivec3(0), icorner-1, greaterThan(icorner-1, ivec3(0))), icorner+17, lessThan(icorner+17, ivec3(0))))-negInnerBlock.xyz;
    bool visible = (corner.x*corner.x + corner.z*corner.z) < (negInnerBlock.w*negInnerBlock.w);
    visible = visible && abs(corner.y) < negInnerBlock.w;
    return visible;
}

#ifdef TAA
vec2 getTAA();
#endif

void main() {
    uint id = (gl_InstanceID<<5)+gl_BaseInstance+(gl_VertexID>>3);

    ivec3 origin = unpackPos(chunkPos[id])*16;
    origin -= cameraBlockPos.xyz;

    if (!shouldRender(origin)) {
        gl_Position = vec4(-100.0f, -100.0f, -100.0f, 0.0f);
        return;
    }

    ivec3 cubeCornerI = ivec3(gl_VertexID&1, (gl_VertexID>>2)&1, (gl_VertexID>>1)&1)*16;
    //Expand the y height to be big (will be +- 8192)
    //TODO: make it W.R.T world height and offsets
    //cubeCornerI.y = cubeCornerI.y*1024-512;
    gl_Position = MVP * vec4(vec3(cubeCornerI+origin), 1);

    //TODO: FIXME with reverse z need tobe + not -
    gl_Position.z += CLOSER_SIGN*0.0005f;//Bring closer to camera

    #ifdef TAA
    gl_Position.xy += getTAA()*gl_Position.w;//Apply TAA if we have it
    #endif
}



//Undefine depth stuff
#import <voxy:util/depthutils.glsl>
