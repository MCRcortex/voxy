package me.cortex.voxy.client.core.rendering.section;


import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.rendering.Viewport;

//Takes in mesh ids from the hierachical traversal and may perform more culling then renders it
public abstract class AbstractSectionRenderer <T extends Viewport<T>> {
    public abstract void renderOpaque(T viewport);
    public abstract void buildDrawCallsAndRenderTemporal(T viewport, GlBuffer sectionRenderList);
    public abstract void renderTranslucent(T viewport);

    public abstract T createViewport();
}
