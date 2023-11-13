package me.cortex.voxelmon.core.rendering;

import me.cortex.voxelmon.core.gl.GlFramebuffer;
import me.cortex.voxelmon.core.gl.GlTexture;

public class PostProcessing {
    private final GlFramebuffer framebuffer;
    private GlTexture colour;
    private GlTexture depth;

    public PostProcessing() {
        this.framebuffer = new GlFramebuffer();
    }


}
