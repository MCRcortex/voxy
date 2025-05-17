
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


layout(binding = HIZ_BINDING) uniform sampler2DShadow hizDepthSampler;

//TODO: maybe do spher bounds aswell? cause they have different accuracies but are both over estimates (liberals (non conservative xD))
// so can do &&

bool within(vec2 a, vec2 b, vec2 c) {
    return all(lessThan(a,b)) && all(lessThan(b, c));
}

bool within(vec3 a, vec3 b, vec3 c) {
    return all(lessThan(a,b)) && all(lessThan(b, c));
}

bool within(float a, float b, float c) {
    return a<b && b<c;
}

float crossMag(vec2 a, vec2 b) {
    return abs(a.x*b.y-b.x*a.y);
}

bool checkPointInView(vec4 point) {
    return within(vec3(-point.w,-point.w,0.0f), point.xyz, vec3(point.w));
}

vec3 minBB = vec3(0.0f);
vec3 maxBB = vec3(0.0f);
vec2 size = vec2(0.0f);
bool insideFrustum = false;

float screenSize = 0.0f;

UnpackedNode node22;
//Sets up screenspace with the given node id, returns true on success false on failure/should not continue
//Accesses data that is setup in the main traversal and is just shared to here
void setupScreenspace(in UnpackedNode node) {
    //TODO: Need to do aabb size for the nodes, it must be an overesimate of all the children

    node22 = node;
    /*
    Transform transform = transforms[getTransformIndex(node)];

    vec3 point = VP*(((transform.transform*vec4((node.pos<<node.lodLevel) - transform.originPos.xyz, 1))
                    + (transform.worldPos.xyz-camChunkPos))-camSubChunk);
                    */


    vec3 basePos = vec3(((node.pos<<node.lodLevel)-camSecPos)<<5)-camSubSecPos;

    insideFrustum = !outsideFrustum(frustum, basePos, float(32<<node.lodLevel));

    //Fast exit
    if (!insideFrustum) {
        return;
    }

    vec4 P000 = VP * vec4(basePos, 1);
    mat3x4 Axis = mat3x4(VP) * float(32<<node.lodLevel);

    vec4 P100 = Axis[0] + P000;
    vec4 P001 = Axis[2] + P000;
    vec4 P101 = Axis[2] + P100;
    vec4 P010 = Axis[1] + P000;
    vec4 P110 = Axis[1] + P100;
    vec4 P011 = Axis[1] + P001;
    vec4 P111 = Axis[1] + P101;


    //Perspective divide + convert to screenspace (i.e. range 0->1 if within viewport)
    vec3 p000 = (P000.xyz/P000.w) * 0.5f + 0.5f;
    vec3 p100 = (P100.xyz/P100.w) * 0.5f + 0.5f;
    vec3 p001 = (P001.xyz/P001.w) * 0.5f + 0.5f;
    vec3 p101 = (P101.xyz/P101.w) * 0.5f + 0.5f;
    vec3 p010 = (P010.xyz/P010.w) * 0.5f + 0.5f;
    vec3 p110 = (P110.xyz/P110.w) * 0.5f + 0.5f;
    vec3 p011 = (P011.xyz/P011.w) * 0.5f + 0.5f;
    vec3 p111 = (P111.xyz/P111.w) * 0.5f + 0.5f;


    {//Compute exact screenspace size
        float ssize = 0;
        {//Faces from 0,0,0

            vec2 A = p100.xy-p000.xy;
            vec2 B = p010.xy-p000.xy;
            vec2 C = p001.xy-p000.xy;
            ssize += crossMag(A,B);
            ssize += crossMag(A,C);
            ssize += crossMag(C,B);
        }
        {//Faces from 1,1,1
            vec2 A = p011.xy-p111.xy;
            vec2 B = p101.xy-p111.xy;
            vec2 C = p110.xy-p111.xy;
            ssize += crossMag(A,B);
            ssize += crossMag(A,C);
            ssize += crossMag(C,B);
        }
        ssize *= 0.5f;//Half the size since we did both back and front area
        screenSize = ssize;
    }

    minBB = min(min(min(p000, p100), min(p001, p101)), min(min(p010, p110), min(p011, p111)));
    maxBB = max(max(max(p000, p100), max(p001, p101)), max(max(p010, p110), max(p011, p111)));

    size = clamp(maxBB.xy - minBB.xy, vec2(0), vec2(1));
}

//Checks if the node is implicitly culled (outside frustum)
bool outsideFrustum() {
    return !insideFrustum;// maxW < 16 is a trick where 16 is the near plane

    //|| any(lessThanEqual(minBB, vec3(0.0f, 0.0f, 0.0f))) || any(lessThanEqual(vec3(1.0f, 1.0f, 1.0f), maxBB));
}

bool isCulledByHiz() {
    vec2 ssize = size * vec2(screenW, screenH);
    float miplevel = log2(max(max(ssize.x, ssize.y),1));

    //TODO: make a path for if the miplevel would result in the textureSampler sampling a size of 1


    miplevel = ceil(miplevel);
    miplevel = clamp(miplevel, 0, 20);

    if (miplevel >= 10.0f) {//Level 9 or 10// TODO: FIX THIS JANK SHIT
        return false;
    }

    vec2 midpoint = (maxBB.xy + minBB.xy)*0.5f;

    float testAgainst = minBB.z;
    //the *2.0f-1.0f converts from the 0->1 range to -1->1 range that depth is in (not having this causes tighter bounds, but causes culling issues in caves)
    testAgainst = testAgainst*2.0f-1.0f;

    bool culled = textureLod(hizDepthSampler, clamp(vec3(midpoint, testAgainst), vec3(0), vec3(1)), miplevel) < 0.0001f;

    //printf("HiZ sample point: (%f,%f)@%f against %f", midpoint.x, midpoint.y, miplevel, minBB.z);
    //if ((culled) && node22.lodLevel == 0) {
    //    printf("HiZ sample point: (%f,%f)@%f against %f, value %f", midpoint.x, midpoint.y, miplevel, minBB.z, textureLod(hizDepthSampler, vec3(0.5f,0.5f, 0.000000001f), 9.0f));
    //}
    return culled;
}



//Returns if we should decend into its children or not
bool shouldDecend() {
    return screenSize > minSSS;
}