package me.cortex.voxy.client.core.rendering;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.gl.shader.PrintfInjector;
import me.cortex.voxy.client.core.model.ModelBakerySubsystem;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.building.RenderGenerationService;
import me.cortex.voxy.client.core.rendering.hierachical2.HierarchicalNodeManager;
import me.cortex.voxy.client.core.rendering.hierachical2.HierarchicalOcclusionTraverser;
import me.cortex.voxy.client.core.rendering.section.AbstractSectionRenderer;
import me.cortex.voxy.client.core.rendering.section.IUsesMeshlets;
import me.cortex.voxy.client.core.rendering.section.MDICSectionRenderer;
import me.cortex.voxy.client.core.rendering.util.DownloadStream;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.client.render.Camera;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.ARBDirectStateAccess.glGetNamedFramebufferAttachmentParameteri;
import static org.lwjgl.opengl.GL42.*;

public class RenderService<T extends AbstractSectionRenderer<J>, J extends Viewport<J>> {
    private static AbstractSectionRenderer<?> createSectionRenderer() {
        return new MDICSectionRenderer();
    }

    private final ViewportSelector<?> viewportSelector;
    private final AbstractSectionRenderer<J> sectionRenderer;

    private final HierarchicalNodeManager nodeManager;
    private final HierarchicalOcclusionTraverser traversal;
    private final ModelBakerySubsystem modelService;
    private final RenderGenerationService renderGen;


    public RenderService(WorldEngine world) {
        this.modelService = new ModelBakerySubsystem(world.getMapper());
        this.nodeManager = new HierarchicalNodeManager(1<<21);

        this.sectionRenderer = (T) createSectionRenderer();
        this.viewportSelector = new ViewportSelector<>(this.sectionRenderer::createViewport);
        this.renderGen = new RenderGenerationService(world, this.modelService, VoxyConfig.CONFIG.renderThreads, this::consumeBuiltSection, this.sectionRenderer instanceof IUsesMeshlets);

        this.traversal = new HierarchicalOcclusionTraverser(this.nodeManager, 512);

        world.setDirtyCallback(section -> System.out.println("Section updated!!: " + WorldEngine.pprintPos(section.key)));

        /*
        for(int x = -200; x<=200;x++) {
            for (int z = -200; z <= 200; z++) {
                for (int y = -3; y <= 3; y++) {
                    this.renderGen.enqueueTask(0, x, y, z);
                }
            }
        }*/
    }

    //Cant do a lambda in the constructor cause "this.nodeManager" could be null??? even tho this does the exact same thing, java is stupid
    private void consumeBuiltSection(BuiltSection section) {this.nodeManager.processBuildResult(section);}

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
        DownloadStream.INSTANCE.tick();
        UploadStream.INSTANCE.tick();

        glMemoryBarrier(GL_FRAMEBUFFER_BARRIER_BIT|GL_PIXEL_BUFFER_BARRIER_BIT);

        int depthBuffer = glGetFramebufferAttachmentParameteri(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        this.traversal.doTraversal(viewport, depthBuffer);

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
