package me.cortex.voxy.client.core.rendering;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.model.OffThreadModelBakerySystem;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.building.RenderGenerationService;
import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.client.render.Camera;

import java.util.List;

public class RenderService {
    private final OffThreadModelBakerySystem modelService;
    private final RenderGenerationService renderGen;

    public RenderService(WorldEngine world) {
        this.modelService = new OffThreadModelBakerySystem(world.getMapper());
        this.renderGen = new RenderGenerationService(world, this.modelService, VoxyConfig.CONFIG.renderThreads, this::consumeRenderBuildResult, false);

        this.renderGen.enqueueTask(0,0,0,0);
    }

    private void consumeRenderBuildResult(BuiltSection section) {
        System.err.println("Section " + WorldEngine.pprintPos(section.position));
        section.free();
    }

    public void setup(Camera camera) {
        this.modelService.syncChanges();
    }

    public void renderFarAwayOpaque(Viewport viewport) {

    }

    public void renderFarAwayTranslucent(Viewport viewport) {

    }

    public void addDebugData(List<String> debug) {
        this.modelService.addDebugData(debug);
        this.renderGen.addDebugData(debug);
    }

    public Viewport<?> createViewport() {
        return new RenderServiceViewport();
    }




    public void shutdown() {
        this.modelService.shutdown();
        this.renderGen.shutdown();
    }
}
