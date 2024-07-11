//This provides per scene/viewport/transfrom access, that is, a node can be attached to a specific scene/viewport/transfrom, this is so that
// different nodes/models can have different viewports/scenes/transfrom which enables some very cool things like
// absolutly massive VS2 structures should... just work :tm: - todd howard

struct Transform {
    mat4 transform;
    ivec4 originPos;
    ivec4 worldPos;
};


layout(binding = TRANSFORM_ARRAY_INDEX, std140) uniform TransformArray {
    Transform transforms[32];
};
