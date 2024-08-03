package me.cortex.voxy.client.core.rendering.section;


import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.rendering.Viewport;
import me.cortex.voxy.client.core.rendering.geometry.OLD.AbstractGeometryManager;

//Takes in mesh ids from the hierachical traversal and may perform more culling then renders it
public abstract class AbstractSectionRenderer <T extends Viewport<T>, J extends AbstractSectionGeometryManager> {
    private final J geometryManager;
    protected AbstractSectionRenderer(J geometryManager) {
        this.geometryManager = geometryManager;
    }

    public abstract void renderOpaque(T viewport);
    public abstract void buildDrawCallsAndRenderTemporal(T viewport, GlBuffer sectionRenderList);
    public abstract void renderTranslucent(T viewport);
    public abstract T createViewport();
    public void free() {
        this.geometryManager.free();
    }

    public J getGeometryManager() {
        return this.geometryManager;
    }
}
