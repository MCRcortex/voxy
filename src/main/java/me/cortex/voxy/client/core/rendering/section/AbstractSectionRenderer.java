package me.cortex.voxy.client.core.rendering.section;


import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.rendering.geometry.OLD.Gl46HierarchicalViewport;

//Takes in mesh ids from the hierachical traversal and may perform more culling then renders it
public abstract class AbstractSectionRenderer {
    public abstract void renderOpaque(Gl46HierarchicalViewport viewport, GlBuffer renderList);
}
