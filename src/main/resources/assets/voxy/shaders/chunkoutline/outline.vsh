#version 460

layout(binding = 0, std140) uniform SceneUniform {
    mat4 MVP;
    ivec4 section;
    vec4 negInnerSec;
};

layout(binding = 1, std430) restrict readonly buffer ChunkPosBuffer {
    ivec2[] chunkPos;
};

ivec3 unpackPos(ivec2 pos) {
    return ivec3(pos.y>>10, (pos.x<<12)>>12, ((pos.y<<22)|int(uint(pos.x)>>10))>>10);
}

bool shouldRender(ivec3 icorner) {
    vec3 corner = vec3(icorner*16)+negInnerSec.xyz;
    //closest point TODO: OPTIMIZE
    corner = mix(mix(vec3(0), corner, lessThan(vec3(0), corner)), corner+16, lessThan(corner+16, vec3(0)));
    return (corner.x*corner.x + corner.z*corner.z < negInnerSec.w*negInnerSec.w) && abs(corner.y) < negInnerSec.w;
}

void main() {
    uint id = (gl_InstanceID<<5)+gl_BaseInstance+(gl_VertexID>>3);

    ivec3 origin = unpackPos(chunkPos[id]);
    origin -= section.xyz;

    if (!shouldRender(origin)) {
        gl_Position = vec4(-100.0f, -100.0f, -100.0f, 0.0f);
        return;
    }

    ivec3 cubeCornerI = ivec3(gl_VertexID&1, (gl_VertexID>>2)&1, (gl_VertexID>>1)&1);
    //Expand the y height to be big (will be +- 8192)
    //TODO: make it W.R.T world height and offsets
    //cubeCornerI.y = cubeCornerI.y*1024-512;
    gl_Position = MVP * vec4(vec3(cubeCornerI+origin)*16, 1);
    gl_Position.z -= 0.0005f;
}