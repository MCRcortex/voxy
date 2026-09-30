#version 450 core

layout(binding = 0) uniform sampler2D depthTex;
layout(location = 1) uniform mat4 invProjMat;
layout(location = 2) uniform mat4 projMat;

#ifdef EMIT_COLOUR
layout(binding = 3) uniform sampler2D colourTex;
#ifdef HAS_FOG
layout(location = 4) uniform vec4 endParams;
layout(location = 5) uniform vec4 fogColour;
#endif
#ifdef HAS_FADE
layout(location = 6) uniform vec4 fadeParams;
#endif
#endif

#import <voxy:util/depthutils.glsl>

out vec4 colour;
in vec2 UV;

vec3 rev3d(vec3 clip) {
    vec4 view = invProjMat * vec4(SCREEN2NDC(clip),1.0f);
    return view.xyz/view.w;
}

float projDepth(vec3 pos) {
    vec4 view = projMat * vec4(pos, 1);
    return view.z/view.w;
}

void main() {
    float depth = texture(depthTex, UV.xy).r;
    if (depth == 0.0f || depth == 1.0f) {
        discard;
    }

    vec3 point = rev3d(vec3(UV.xy, depth));
    depth = projDepth(point);
    //TODO: HERE make an option/define to emit the output depth as something other then the input (i.e. if voxy is reverse z and vanilla isnt, transform and emit as not reverrse z)
    depth = REDUCTION2(FAR+CLOSER_SIGN*(2.0f/((1<<24)-1)), depth);
    depth = NDC2SCREEN_DEPTH(depth);

    depth = gl_DepthRange.diff * depth + gl_DepthRange.near;//TODO: dont think this is right at all so should fix this

    gl_FragDepth = depth;

    #ifdef EMIT_COLOUR
    colour = texture(colourTex, UV.xy);
    if (colour.a == 0.0) {
        discard;
    }
    #ifdef HAS_FOG
    if (fogColour.a>0.0){
        float fogLerp = clamp(fma(length(point.xyz),endParams.x,endParams.y),0,endParams.z);//512 is 32*16 which is the render distance in blocks
        colour.rgb = mix(colour.rgb, fogColour.rgb, fogLerp*fogColour.a);
    }
    #endif
    #ifdef HAS_FADE
    //yes am aware how inefficent this is, this could all be packed into endParams.w
    if (fadeParams.x>0) {
        float len = fadeParams.x>1.5?length(point.xyz):length(point.xz);
        colour.a *= 1-clamp(fma(len, fadeParams.z, fadeParams.y), 0,1);
    }
    //TODO: maybe discard if colour.a == 0?
    #endif
    #else
    colour = vec4(0);
    #endif

}