package me.cortex.voxy.client.core.rendering;

import me.cortex.voxy.client.RenderStatistics;
import me.cortex.voxy.client.TimingStatistics;
import me.cortex.voxy.client.VoxyClient;
import me.cortex.voxy.client.core.gl.Capabilities;
import me.cortex.voxy.client.core.gl.GlTexture;
import me.cortex.voxy.client.core.model.ModelBakerySubsystem;
import me.cortex.voxy.client.core.rendering.building.RenderGenerationService;
import me.cortex.voxy.client.core.rendering.hierachical.AsyncNodeManager;
import me.cortex.voxy.client.core.rendering.hierachical.HierarchicalOcclusionTraverser;
import me.cortex.voxy.client.core.rendering.hierachical.NodeCleaner;
import me.cortex.voxy.client.core.rendering.section.AbstractSectionRenderer;
import me.cortex.voxy.client.core.rendering.section.geometry.*;
import me.cortex.voxy.client.core.rendering.section.IUsesMeshlets;
import me.cortex.voxy.client.core.rendering.section.MDICSectionRenderer;
import me.cortex.voxy.client.core.rendering.util.DownloadStream;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.thread.ServiceThreadPool;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.lwjgl.opengl.GL42.*;

public class RenderService<T extends AbstractSectionRenderer<J, Q>, J extends Viewport<J>, Q extends IGeometryData> {
    public static final int STATIC_VAO = glGenVertexArrays();

    private final ViewportSelector<?> viewportSelector;
    private final Q geometryData;
    private final AbstractSectionRenderer<J, Q> sectionRenderer;

    private final AsyncNodeManager nodeManager;
    private final NodeCleaner nodeCleaner;
    private final HierarchicalOcclusionTraverser traversal;
    private final ModelBakerySubsystem modelService;
    private final RenderGenerationService renderGen;

    private final WorldEngine world;

    private static long getGeometryBufferSize() {
        long geometryCapacity = Math.min((1L<<(64-Long.numberOfLeadingZeros(Capabilities.INSTANCE.ssboMaxSize-1)))<<1, 1L<<32)-1024/*(1L<<32)-1024*/;
        if (Capabilities.INSTANCE.isIntel) {
            geometryCapacity = Math.max(geometryCapacity, 1L<<30);//intel moment, force min 1gb
        }

        //Limit to available dedicated memory if possible
        if (Capabilities.INSTANCE.canQueryGpuMemory) {
            //512mb less than avalible,
            long limit = Capabilities.INSTANCE.getFreeDedicatedGpuMemory() - 1024*1024*1024;
            // Give a minimum of 512 mb requirement
            limit = Math.max(512*1024*1024, limit);

            geometryCapacity = Math.min(geometryCapacity, limit);
        }
        //geometryCapacity = 1<<28;
        //geometryCapacity = 1<<30;//1GB test
        return geometryCapacity;
    }

    @SuppressWarnings("unchecked")
    public RenderService(WorldEngine world, ServiceThreadPool serviceThreadPool) {
        this.world = world;
        this.modelService = new ModelBakerySubsystem(world.getMapper());

        long geometryCapacity = getGeometryBufferSize();

        this.geometryData = (Q) new BasicSectionGeometryData(1<<20, geometryCapacity);

        //Max sections: ~500k
        this.sectionRenderer = (T) new MDICSectionRenderer(this.modelService.getStore(), (BasicSectionGeometryData) this.geometryData);
        Logger.info("Using renderer: " + this.sectionRenderer.getClass().getSimpleName() + " with geometry buffer of: " + geometryCapacity + " bytes");

        //Do something incredibly hacky, we dont need to keep the reference to this around, so just connect and discard

        this.viewportSelector = new ViewportSelector<>(this.sectionRenderer::createViewport);
        this.renderGen = new RenderGenerationService(world, this.modelService, serviceThreadPool,
                this.sectionRenderer.getGeometryManager() instanceof IUsesMeshlets,
                ()->true);

        this.nodeManager = new AsyncNodeManager(1<<21, this.geometryData, this.renderGen);
        this.nodeCleaner = new NodeCleaner(this.nodeManager);

        this.traversal = new HierarchicalOcclusionTraverser(this.nodeManager, this.nodeCleaner, this.renderGen);

        world.setDirtyCallback(this.nodeManager::worldEvent);

        Arrays.stream(world.getMapper().getBiomeEntries()).forEach(this.modelService::addBiome);
        world.getMapper().setBiomeCallback(this.modelService::addBiome);

        this.nodeManager.start();
    }

    public void addTopLevelNode(long pos) {
        this.nodeManager.addTopLevel(pos);
    }

    public void removeTopLevelNode(long pos) {
        this.nodeManager.removeTopLevel(pos);
    }

    public void tickModelService(long budget) {
        this.modelService.tick(budget);
    }

    public void renderFarAwayOpaque(J viewport, GlTexture depthBoundTexture) {
        //LightMapHelper.tickLightmap();

        //Render previous geometry with the abstract renderer
        //Execute the hieracial selector
        // render delta sections

        //Hieracial is not an abstract thing but
        // the section renderer is as it might have different backends, but they all accept a buffer containing the section list


        TimingStatistics.G.start();
        this.sectionRenderer.renderOpaque(viewport, depthBoundTexture);
        TimingStatistics.G.stop();

        {
            int depthBuffer = glGetFramebufferAttachmentParameteri(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);

            //Compute the mip chain
            viewport.hiZBuffer.buildMipChain(depthBuffer, viewport.width, viewport.height);
        }

        do {
            //NOTE: need to do the upload and download tick here, after the section renderer renders the world, to ensure "stable"
            // sections
            {
                TimingStatistics.main.stop();
                TimingStatistics.dynamic.start();

            /*
            this.sectionUpdateQueue.consume(128);

            //if (this.modelService.getProcessingCount() < 750)
            {//Very bad hack to try control things
                this.geometryUpdateQueue.consumeNano(Math.max(3_000_000 - (System.nanoTime() - frameStart), 50_000));
            }

            if (this.nodeManager.writeChanges(this.traversal.getNodeBuffer())) {//TODO: maybe move the node buffer out of the traversal class
                UploadStream.INSTANCE.commit();
            }*/


                TimingStatistics.D.start();
                //Tick download stream
                DownloadStream.INSTANCE.tick();
                TimingStatistics.D.stop();

                this.nodeManager.tick(this.traversal.getNodeBuffer(), this.nodeCleaner);
                //glFlush();

                this.nodeCleaner.tick(this.traversal.getNodeBuffer());//Probably do this here??

                TimingStatistics.dynamic.stop();
                TimingStatistics.main.start();
            }

            glMemoryBarrier(GL_FRAMEBUFFER_BARRIER_BIT | GL_PIXEL_BUFFER_BARRIER_BIT);

            TimingStatistics.I.start();
            this.traversal.doTraversal(viewport);
            TimingStatistics.I.stop();


            if (VoxyClient.isFrexActive()) {//If frex is running we must tick everything to ensure correctness
                UploadStream.INSTANCE.tick();
                //Done here as is allows less gl state resetup
                this.tickModelService(100_000_000);
                glFinish();
            }
        } while (VoxyClient.isFrexActive() && (this.nodeManager.hasWork() || this.renderGen.getTaskCount()!=0 || !this.modelService.areQueuesEmpty()));


        TimingStatistics.H.start();
        this.sectionRenderer.buildDrawCalls(viewport);
        TimingStatistics.H.stop();

        TimingStatistics.G.start();
        this.sectionRenderer.renderTemporal(viewport, depthBoundTexture);
        TimingStatistics.G.stop();
    }

    public void renderFarAwayTranslucent(J viewport, GlTexture depthBoundTexture) {
        this.sectionRenderer.renderTranslucent(viewport, depthBoundTexture);
    }

    public void addDebugData(List<String> debug) {
        this.modelService.addDebugData(debug);
        this.renderGen.addDebugData(debug);
        this.sectionRenderer.addDebug(debug);
        this.nodeManager.addDebug(debug);

        if (RenderStatistics.enabled) {
            debug.add("HTC: [" + Arrays.stream(flipCopy(RenderStatistics.hierarchicalTraversalCounts)).mapToObj(Integer::toString).collect(Collectors.joining(", "))+"]");
            debug.add("HRS: [" + Arrays.stream(flipCopy(RenderStatistics.hierarchicalRenderSections)).mapToObj(Integer::toString).collect(Collectors.joining(", "))+"]");
            debug.add("VS: [" + Arrays.stream(flipCopy(RenderStatistics.visibleSections)).mapToObj(Integer::toString).collect(Collectors.joining(", "))+"]");
            debug.add("QC: [" + Arrays.stream(flipCopy(RenderStatistics.quadCount)).mapToObj(Integer::toString).collect(Collectors.joining(", "))+"]");
        }
    }

    private static int[] flipCopy(int[] array) {
        int[] ret = new int[array.length];
        int i = ret.length;
        for (int j : array) {
            ret[--i] = j;
        }
        return ret;
    }

    public void shutdown() {
        //Cleanup callbacks
        this.world.setDirtyCallback(null);
        this.world.getMapper().setBiomeCallback(null);
        this.world.getMapper().setStateCallback(null);

        this.nodeManager.stop();

        this.modelService.shutdown();
        this.renderGen.shutdown();
        this.viewportSelector.free();
        this.sectionRenderer.free();
        this.traversal.free();
        this.nodeCleaner.free();

        this.geometryData.free();
    }

    public Viewport<?> getViewport() {
        return this.viewportSelector.getViewport();
    }

    public int getMeshQueueCount() {
        return this.renderGen.getTaskCount();
    }
}
