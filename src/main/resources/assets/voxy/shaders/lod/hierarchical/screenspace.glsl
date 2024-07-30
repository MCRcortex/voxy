
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


//Sets up screenspace with the given node id, returns true on success false on failure/should not continue
//Accesses data that is setup in the main traversal and is just shared to here
void setupScreenspace(in UnpackedNode node) {
    //TODO: Need to do aabb size for the nodes, it must be an overesimate of all the children

    Transform transform = transforms[getTransformIndex(node)];

    /*
    vec3 point = VP*(((transform.transform*vec4((node.pos<<node.lodLevel) - transform.originPos.xyz, 1))
                    + (transform.worldPos.xyz-camChunkPos))-camSubChunk);
                    */


    vec4 base = VP*vec4(vec3(((node.pos<<node.lodLevel)-camSecPos)<<5)-camSubSecPos, 1);

    //TODO: AABB SIZES not just a max cube

    //vec3 minPos = minSize + basePos;
    //vec3 maxPos = maxSize + basePos;

    minBB = base.xyz/base.w;
    maxBB = minBB;

    for (int i = 1; i < 8; i++) {
        //NOTE!: cant this be precomputed and put in an array?? in the scene uniform??
        vec4 pPoint = (VP*vec4(vec3((i&1)!=0,(i&2)!=0,(i&4)!=0),1))*(32<<node.lodLevel);//Size of section is 32x32x32 (need to change it to a bounding box in the future)
        pPoint += base;
        vec3 point = pPoint.xyz/pPoint.w;
        //TODO: CLIP TO VIEWPORT
        minBB = min(minBB, point);
        maxBB = max(maxBB, point);
    }

    //TODO: MORE ACCURATLY DETERMIN SCREENSPACE AREA, this can be done by computing and adding
    //  the projected surface area of each face/quad which winding order faces the camera
    //  (this is just the dot product of 2 projected vectors)

    //can do a funny by not doing the perspective divide except on the output of the area

    //printf("Screenspace MIN: %f, %f, %f  MAX: %f, %f, %f", minBB.x,minBB.y,minBB.z, maxBB.x,maxBB.y,maxBB.z);

    size = maxBB.xy - minBB.xy;

}

//Checks if the node is implicitly culled (outside frustum)
bool outsideFrustum() {
    return any(lessThanEqual(maxBB, vec3(-1f, -1f, 0f))) || any(lessThanEqual(vec3(1f, 1f, 1f), minBB));
}

bool isCulledByHiz() {
    if (minBB.z < 0) {//Minpoint is behind the camera, its always going to pass
        return false;
    }
    vec2 ssize = size.xy * vec2(ivec2(screenW, screenH));
    float miplevel = ceil(log2(max(max(ssize.x, ssize.y),1)));
    vec2 midpoint = (maxBB.xy + minBB.xy)*0.5;
    return textureLod(hizDepthSampler, vec3(midpoint, minBB.z), miplevel) > 0.0001;
}

//Returns if we should decend into its children or not
bool shouldDecend() {
    //printf("Screen area %f: %f, %f", (size.x*size.y*float(screenW)*float(screenH)), float(screenW), float(screenH));
    return (size.x*size.y*screenW*screenH) > decendSSS;
}