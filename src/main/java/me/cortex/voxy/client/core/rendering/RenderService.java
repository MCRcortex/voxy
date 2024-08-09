package me.cortex.voxy.client.core.rendering;

import me.cortex.voxy.client.core.model.ModelBakerySubsystem;
import me.cortex.voxy.client.core.model.ModelStore;
import me.cortex.voxy.client.core.rendering.building.RenderGenerationService;
import me.cortex.voxy.client.core.rendering.building.SectionPositionUpdateFilterer;
import me.cortex.voxy.client.core.rendering.building.SectionUpdate;
import me.cortex.voxy.client.core.rendering.hierachical2.HierarchicalNodeManager;
import me.cortex.voxy.client.core.rendering.hierachical2.HierarchicalOcclusionTraverser;
import me.cortex.voxy.client.core.rendering.section.AbstractSectionRenderer;
import me.cortex.voxy.client.core.rendering.section.IUsesMeshlets;
import me.cortex.voxy.client.core.rendering.section.MDICSectionRenderer;
import me.cortex.voxy.client.core.rendering.util.DownloadStream;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.thread.ServiceThreadPool;
import net.minecraft.client.render.Camera;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.lwjgl.opengl.GL42.*;

public class RenderService<T extends AbstractSectionRenderer<J, ?>, J extends Viewport<J>> {
    public static final int STATIC_VAO = glGenVertexArrays();

    private static AbstractSectionRenderer<?, ?> createSectionRenderer(ModelStore store, int maxSectionCount, long geometryCapacity) {
        return new MDICSectionRenderer(store, maxSectionCount, geometryCapacity);
    }

    private final ViewportSelector<?> viewportSelector;
    private final AbstractSectionRenderer<J, ?> sectionRenderer;

    private final HierarchicalNodeManager nodeManager;
    private final HierarchicalOcclusionTraverser traversal;
    private final ModelBakerySubsystem modelService;
    private final RenderGenerationService renderGen;

    private final ConcurrentLinkedDeque<SectionUpdate> sectionUpdateQueue = new ConcurrentLinkedDeque<>();

    public RenderService(WorldEngine world, ServiceThreadPool serviceThreadPool) {
        this.modelService = new ModelBakerySubsystem(world.getMapper());

        //Max sections: ~500k
        //Max geometry: 1 gb
        this.sectionRenderer = (T) createSectionRenderer(this.modelService.getStore(),1<<19, (1L<<30)-1024);

        //Do something incredibly hacky, we dont need to keep the reference to this around, so just connect and discard
        var positionFilterForwarder = new SectionPositionUpdateFilterer();

        this.nodeManager = new HierarchicalNodeManager(1<<21, this.sectionRenderer.getGeometryManager(), positionFilterForwarder);

        this.viewportSelector = new ViewportSelector<>(this.sectionRenderer::createViewport);
        this.renderGen = new RenderGenerationService(world, this.modelService, serviceThreadPool, this.sectionUpdateQueue::add, this.sectionRenderer.getGeometryManager() instanceof IUsesMeshlets);

        positionFilterForwarder.setCallbacks(this.renderGen::enqueueTask, section -> {
            long time = System.nanoTime();
            byte childExistence = section.getNonEmptyChildren();

            this.sectionUpdateQueue.add(new SectionUpdate(section.key, time, null, childExistence));
        });

        this.traversal = new HierarchicalOcclusionTraverser(this.nodeManager, 512);

        world.setDirtyCallback(positionFilterForwarder::maybeForward);

        Arrays.stream(world.getMapper().getBiomeEntries()).forEach(this.modelService::addBiome);
        world.getMapper().setBiomeCallback(this.modelService::addBiome);


        for (int x = -10; x <= 10; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -10; z <= 10; z++) {
                    positionFilterForwarder.watch(0, x, y ,z);
                }
            }
        }

    }

    public void setup(Camera camera) {
        this.modelService.tick();
    }

    public void renderFarAwayOpaque(J viewport) {
        LightMapHelper.tickLightmap();

        //Render previous geometry with the abstract renderer
        //Execute the hieracial selector
        // render delta sections

        //Hieracial is not an abstract thing but
        // the section renderer is as it might have different backends, but they all accept a buffer containing the section list

        this.sectionRenderer.renderOpaque(viewport);


        //NOTE: need to do the upload and download tick here, after the section renderer renders the world, to ensure "stable"
        // sections


        //FIXME: we only want to tick once per full frame, this is due to how the data of sections is updated
        // we basicly need the data to stay stable from one frame to the next, till after renderOpaque
        // this is because e.g. shadows, cause this pipeline to be invoked multiple times
        // which may cause the geometry to become outdated resulting in corruption rendering in renderOpaque
        //TODO: Need to find a proper way to fix this (if there even is one)
        if (true /* firstInvocationThisFrame */) {
            DownloadStream.INSTANCE.tick();
            //Process the build results here (this is done atomically/on the render thread)
            while (!this.sectionUpdateQueue.isEmpty()) {
                this.nodeManager.processResult(this.sectionUpdateQueue.poll());
            }
        }
        UploadStream.INSTANCE.tick();

        glMemoryBarrier(GL_FRAMEBUFFER_BARRIER_BIT|GL_PIXEL_BUFFER_BARRIER_BIT);

        int depthBuffer = glGetFramebufferAttachmentParameteri(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        this.traversal.doTraversal(viewport, depthBuffer);

        this.sectionRenderer.buildDrawCallsAndRenderTemporal(viewport, this.traversal.getRenderListBuffer());
    }

    public void renderFarAwayTranslucent(J viewport) {
        this.sectionRenderer.renderTranslucent(viewport);
    }

    public void addDebugData(List<String> debug) {
        this.modelService.addDebugData(debug);
        this.renderGen.addDebugData(debug);
        this.sectionRenderer.addDebug(debug);
    }

    public void shutdown() {
        this.modelService.shutdown();
        this.renderGen.shutdown();
        this.viewportSelector.free();
        this.sectionRenderer.free();
        this.traversal.free();
        //Release all the unprocessed built geometry
        this.sectionUpdateQueue.forEach(update -> {if(update.geometry()!=null)update.geometry().free();});
        this.sectionUpdateQueue.clear();
    }

    public Viewport<?> getViewport() {
        return this.viewportSelector.getViewport();
    }
}
