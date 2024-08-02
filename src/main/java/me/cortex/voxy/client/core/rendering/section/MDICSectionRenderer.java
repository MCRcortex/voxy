package me.cortex.voxy.client.core.rendering.section;


import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.rendering.geometry.OLD.Gl46HierarchicalViewport;

//Uses MDIC to render the sections
public class MDICSectionRenderer extends AbstractSectionRenderer<BasicViewport> {
    @Override
    public void renderOpaque(BasicViewport viewport) {

    }

    @Override
    public void buildDrawCallsAndRenderTemporal(BasicViewport viewport, GlBuffer sectionRenderList) {

    }

    @Override
    public void renderTranslucent(BasicViewport viewport) {

    }

    @Override
    public BasicViewport createViewport() {
        return new BasicViewport();
    }

    @Override
    public void free() {

    }
}
