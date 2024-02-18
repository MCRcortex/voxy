#line 1
//TODO: FIXME: this isnt actually correct cause depending on the face (i think) it could be 1/64 th of a position off
// but im going to assume that since we are dealing with huge render distances, this shouldent matter that much
float extractFaceIndentation(uint faceData) {
    return float((faceData>>16)&63u)/63.0;
}

vec4 extractFaceSizes(uint faceData) {
    return (vec4(faceData&0xFu, (faceData>>4)&0xFu, (faceData>>8)&0xFu, (faceData>>12)&0xFu)/16.0)+vec4(0.0,1.0/16.0,0.0,1.0/16.0);
}

uint faceHasAlphaCuttout(uint faceData) {
    return (faceData>>22)&1u;
}

//TODO: try and get rid of
uint faceHasAlphaCuttoutOverride(uint faceData) {
    return (faceData>>23)&1u;
}

bool modelHasBiomeLUT(BlockModel model) {
    return ((model.flagsA)&2) != 0;
}

bool modelIsTranslucent(BlockModel model) {
    return ((model.flagsA)&4) != 0;
}

bool modelHasMipmaps(BlockModel model) {
    return ((model.flagsA)&8) != 0;
}