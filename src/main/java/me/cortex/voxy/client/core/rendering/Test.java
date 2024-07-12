package me.cortex.voxy.client.core.rendering;


import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME;

public class Test {
    private final HierarchicalOcclusionRenderer hor = new HierarchicalOcclusionRenderer();

    public void doIt(Viewport viewport) {
        var i = new int[1];
        glGetFramebufferAttachmentParameteriv(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME, i);
        this.hor.render(i[0], viewport.width, viewport.height);
    }
}
