package me.cortex.voxy.client.core.rendering.geometry.OLD;

import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.rendering.Viewport;

import static org.lwjgl.opengl.ARBIndirectParameters.glMultiDrawElementsIndirectCountARB;
import static org.lwjgl.opengl.GL45C.glClearNamedBufferData;

public class Gl46Viewport extends Viewport<Gl46Viewport> {
    GlBuffer visibilityBuffer;
    public Gl46Viewport(Gl46FarWorldRenderer renderer) {
        this.visibilityBuffer = new GlBuffer(renderer.maxSections*4L).zero();
    }

    protected void delete0() {
        this.visibilityBuffer.free();
    }
}
