package me.cortex.voxy.client.core.rendering.section;


import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.model.ModelStore;
import me.cortex.voxy.client.core.rendering.Viewport;
import me.cortex.voxy.client.core.rendering.geometry.OLD.AbstractGeometryManager;

import java.util.List;

//Takes in mesh ids from the hierachical traversal and may perform more culling then renders it
public abstract class AbstractSectionRenderer <T extends Viewport<T>, J extends AbstractSectionGeometryManager> {
    protected final J geometryManager;
    protected final ModelStore modelStore;
    protected AbstractSectionRenderer(ModelStore modelStore, J geometryManager) {
        this.geometryManager = geometryManager;
        this.modelStore = modelStore;
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

    public void addDebug(List<String> lines) {}
}
