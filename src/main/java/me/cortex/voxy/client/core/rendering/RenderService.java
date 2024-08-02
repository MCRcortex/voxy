package me.cortex.voxy.client.core.rendering;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.model.ModelBakerySubsystem;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.building.RenderGenerationService;
import me.cortex.voxy.client.core.rendering.section.AbstractSectionRenderer;
import me.cortex.voxy.client.core.rendering.section.MDICSectionRenderer;
import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.client.render.Camera;

import java.util.List;

public class RenderService {
    private final ViewportSelector<?> viewportSelector;
    private final AbstractSectionRenderer sectionRenderer;
    private final ModelBakerySubsystem modelService;
    private final RenderGenerationService renderGen;

    public RenderService(WorldEngine world) {
        this.modelService = new ModelBakerySubsystem(world.getMapper());
        this.sectionRenderer = new MDICSectionRenderer();
        this.renderGen = new RenderGenerationService(world, this.modelService, VoxyConfig.CONFIG.renderThreads, this::consumeRenderBuildResult, false);

        this.viewportSelector = new ViewportSelector<>(this.sectionRenderer::createViewport);


        for(int x = -200; x<=200;x++) {
            for (int z = -200; z <= 200; z++) {
                for (int y = -3; y <= 3; y++) {
                    this.renderGen.enqueueTask(0, x, y, z);
                }
            }
        }
    }

    private void consumeRenderBuildResult(BuiltSection section) {
        //System.err.println("Section " + WorldEngine.pprintPos(section.position));
        section.free();
    }

    public void setup(Camera camera) {
        this.modelService.tick();
    }

    public void renderFarAwayOpaque(Viewport viewport) {
        //Render previous geometry with the abstract renderer
        //Execute the hieracial selector
        // render delta sections

        //Hieracial is not an abstract thing but
        // the section renderer is as it might have different backends, but they all accept a buffer containing the section list

    }

    public void renderFarAwayTranslucent(Viewport viewport) {

    }

    public void addDebugData(List<String> debug) {
        this.modelService.addDebugData(debug);
        this.renderGen.addDebugData(debug);
    }

    public void shutdown() {
        this.modelService.shutdown();
        this.renderGen.shutdown();
        this.viewportSelector.free();
    }

    public Viewport<?> getViewport() {
        return this.viewportSelector.getViewport();
    }
}
