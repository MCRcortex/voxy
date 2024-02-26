package me.cortex.voxy.client.core.rendering;

import me.cortex.voxy.client.core.gl.GlBuffer;

import static org.lwjgl.opengl.ARBIndirectParameters.glMultiDrawElementsIndirectCountARB;
import static org.lwjgl.opengl.GL30C.GL_R8UI;
import static org.lwjgl.opengl.GL30C.GL_RED_INTEGER;
import static org.lwjgl.opengl.GL42.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL45C.glClearNamedBufferData;

public class Gl46Viewport extends Viewport {
    GlBuffer visibilityBuffer;
    public Gl46Viewport(int maxSections) {
        this.visibilityBuffer = new GlBuffer(maxSections*4L);
        glClearNamedBufferData(this.visibilityBuffer.id, GL_R8UI, GL_RED_INTEGER, GL_UNSIGNED_BYTE, new int[1]);
    }

    @Override
    public void delete() {
        this.visibilityBuffer.free();
    }
}
