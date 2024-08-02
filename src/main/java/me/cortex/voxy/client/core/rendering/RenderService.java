package me.cortex.voxy.client.core.rendering;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.model.ModelBakerySubsystem;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.building.RenderGenerationService;
import me.cortex.voxy.client.core.rendering.hierarchical.HierarchicalOcclusionTraverser;
import me.cortex.voxy.client.core.rendering.section.AbstractSectionRenderer;
import me.cortex.voxy.client.core.rendering.section.MDICSectionRenderer;
import me.cortex.voxy.client.core.rendering.util.DownloadStream;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.client.render.Camera;

import java.util.List;

public class RenderService<T extends AbstractSectionRenderer<J>, J extends Viewport<J>> {
    private final ViewportSelector<?> viewportSelector;
    private final T sectionRenderer;
    private final HierarchicalOcclusionTraverser traversal;
    private final ModelBakerySubsystem modelService;
    private final RenderGenerationService renderGen;

    public RenderService(WorldEngine world) {
        this.modelService = new ModelBakerySubsystem(world.getMapper());
        this.sectionRenderer = (T) createSectionRenderer();
        this.renderGen = new RenderGenerationService(world, this.modelService, VoxyConfig.CONFIG.renderThreads, this::consumeBuiltSection, false);
        this.traversal = new HierarchicalOcclusionTraverser(this.renderGen, null);

        world.setDirtyCallback(section -> System.out.println("Section updated!!: " + WorldEngine.pprintPos(section.key)));

        this.viewportSelector = new ViewportSelector<>(this.sectionRenderer::createViewport);


        for(int x = -400; x<=400;x++) {
            for (int z = -400; z <= 400; z++) {
                for (int y = -6; y <= 6; y++) {
                    this.renderGen.enqueueTask(0, x, y, z);
                }
            }
        }
    }

    private void consumeBuiltSection(BuiltSection section) {
        this.traversal.consumeBuiltSection(section);
    }

    private static AbstractSectionRenderer<?> createSectionRenderer() {
        return new MDICSectionRenderer();
    }

    public void setup(Camera camera) {
        this.modelService.tick();
    }

    public void renderFarAwayOpaque(J viewport) {
        //Render previous geometry with the abstract renderer
        //Execute the hieracial selector
        // render delta sections

        //Hieracial is not an abstract thing but
        // the section renderer is as it might have different backends, but they all accept a buffer containing the section list

        this.sectionRenderer.renderOpaque(viewport);
        //NOTE: need to do the upload and download tick here, after the section renderer renders the world, to ensure "stable"
        // sections
        UploadStream.INSTANCE.tick();
        DownloadStream.INSTANCE.tick();

        this.traversal.doTraversal(viewport);

        this.sectionRenderer.buildDrawCallsAndRenderTemporal(viewport, null);
    }

    public void renderFarAwayTranslucent(J viewport) {
        this.sectionRenderer.renderTranslucent(viewport);
    }

    public void addDebugData(List<String> debug) {
        this.modelService.addDebugData(debug);
        this.renderGen.addDebugData(debug);
    }

    public void shutdown() {
        this.modelService.shutdown();
        this.renderGen.shutdown();
        this.viewportSelector.free();
        this.sectionRenderer.free();
        this.traversal.free();
    }

    public Viewport<?> getViewport() {
        return this.viewportSelector.getViewport();
    }
}
