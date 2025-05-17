package me.cortex.voxy.client.core.rendering.section;


import me.cortex.voxy.client.core.gl.GlTexture;
import me.cortex.voxy.client.core.model.ModelStore;
import me.cortex.voxy.client.core.rendering.Viewport;
import me.cortex.voxy.client.core.rendering.section.geometry.IGeometryData;

import java.util.List;

//Takes in mesh ids from the hierachical traversal and may perform more culling then renders it
public abstract class AbstractSectionRenderer <T extends Viewport<T>, J extends IGeometryData> {
    protected final J geometryManager;
    protected final ModelStore modelStore;
    protected AbstractSectionRenderer(ModelStore modelStore, J geometryManager) {
        this.geometryManager = geometryManager;
        this.modelStore = modelStore;
    }

    public abstract void renderOpaque(T viewport, GlTexture depthBoundTexture);
    public abstract void buildDrawCalls(T viewport);
    public abstract void renderTemporal(GlTexture depthBoundTexture);
    public abstract void renderTranslucent(T viewport, GlTexture depthBoundTexture);
    public abstract T createViewport();
    public abstract void free();

    public J getGeometryManager() {
        return this.geometryManager;
    }

    public void addDebug(List<String> lines) {}
}
