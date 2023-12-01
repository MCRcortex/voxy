layout(binding=0) uniform sampler2D colourTexture;
in vec2 uv;
void main() {
    gl_Colour = texture(colourTexture, uv);
}