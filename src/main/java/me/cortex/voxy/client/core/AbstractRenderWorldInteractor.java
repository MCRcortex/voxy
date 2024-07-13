package me.cortex.voxy.client.core;

import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.building.RenderGenerationService;
import me.cortex.voxy.common.world.WorldSection;

public interface AbstractRenderWorldInteractor {
    void processBuildResult(BuiltSection section);

    void sectionUpdated(WorldSection worldSection);

    void setRenderGen(RenderGenerationService renderService);

    void initPosition(int x, int z);

    void setCenter(int x, int y, int z);
}
