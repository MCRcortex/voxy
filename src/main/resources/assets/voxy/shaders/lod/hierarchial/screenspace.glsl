
//All the screenspace computuation code, hiz culling + size/screenspace AABB size computation
// to determin whether child node should be visited
// it controls the actions of the traversal logic
//NOTEEE!!! SO can do a few things, technically since atm its split not useing persistent threads
// can use mesh shaders to do rasterized occlution directly with a meshdrawindirect, one per layer
//Persistent threads might still be viable/usable since the inital lods supplied to the culler are mixed level
// (basiclly the minimum guarenteed value, like dont supply a top level lod right in front of the camera, since that is guarenteed not to, never be that level)
// do this based on camera distance computation

//changing the base level/root of the graph for some nodes can be really tricky and incorrect so might not be worth it but it should help
// substantually for performance (for both persistent threads and incremental)


layout(binding = HIZ_BINDING_INDEX) uniform sampler2DShadow hizDepthSampler;

//TODO: maybe do spher bounds aswell? cause they have different accuracies but are both over estimates (liberals (non conservative xD))
// so can do &&

vec3 minBB;
vec3 maxBB;
vec2 size;

vec3 projPoint(mat4 mat, vec3 pos) {
    vec4 t = mat * vec4(vec3(pos),1);
    return t.xyz/t.w;
}

//Sets up screenspace with the given node id, returns true on success false on failure/should not continue
//Accesses data that is setup in the main traversal and is just shared to here
void setupScreenspace(in UnpackedNode node) {
    //TODO: Need to do aabb size for the nodes, it must be an overesimate of all the children

    mat4 mvp;

    vec3 basePos;
    vec3 minSize;
    vec3 maxSize;


    vec3 minPos = minSize + basePos;
    vec3 maxPos = maxSize + basePos;

    minBB = projPoint(mvp, minPos);
    maxBB = minBB;

    for (int i = 1; i < 8; i++) {
        vec3 point = projPoint(mvp, mix(minPos, maxPos, bvec3((i&1)!=0,(i&2)!=0,(i&4)!=0)));
        minBB = min(minBB, point);
        maxBB = max(maxBB, point);
    }

    size = maxBB.xy - minBB.xy;
}

bool isCulledByHiz() {
    vec2 ssize = size.xy * vec2(ivec2(screensize));
    float miplevel = ceil(log2(max(max(ssize.x, ssize.y),1)));
    vec2 midpoint = (maxBB.xy + minBB.xy)*0.5;
    return textureLod(hizDepthSampler, vec3(midpoint, minBB.z), miplevel) > 0.0001;
}

//Returns if we should decend into its children or not
bool shouldDecend() {
    return (size.x*size.y) > (64*64F);
}