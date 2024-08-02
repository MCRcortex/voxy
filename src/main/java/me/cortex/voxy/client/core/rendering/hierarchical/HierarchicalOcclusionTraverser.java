package me.cortex.voxy.client.core.rendering.hierarchical;

import me.cortex.voxy.client.core.rendering.Viewport;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.building.RenderGenerationService;
import me.cortex.voxy.client.core.rendering.section.AbstractSectionGeometryManager;

public class HierarchicalOcclusionTraverser {
    public HierarchicalOcclusionTraverser(RenderGenerationService renderGenerationService, AbstractSectionGeometryManager sectionGeometryManager) {

    }

    public void doTraversal(Viewport<?> viewport) {

    }

    public void free() {

    }


    public void consumeBuiltSection(BuiltSection section) {
        section.free();
    }
}
