//Contains the definision of a ray and step functions
struct Ray {
    ivec3 pos;
    vec3 innerPos;

    vec3 dir;
    vec3 invDir;
};

Ray ray;
void setup(vec3 origin, vec3 direction) {
    ray.pos = ivec3(origin);
    ray.innerPos = origin - ray.pos;
    direction *= inversesqrt(direction);
    ray.dir = direction;
    ray.invDir = 1/direction;
}

void step(ivec3 aabb) {
    //TODO:check for innerPos>=1 and step into that voxel
    vec3 t2f = (aabb - ray.innerPos) * ray.invDir;
    float mint2f = min(t2f.x, min(t2f.y, t2f.z));
    bvec3 msk = lessThanEqual(t2f.xyz, vec3(mint2f));
    vec3 newIP = mint2f * ray.dir + ray.innerPos;
    ivec3 offset = min(aabb-1, ivec3(newIP));
    ray.pos += offset + ivec3(msk);
    ray.innerPos = mix(vec3(0), newIP - offset, not(msk));
}