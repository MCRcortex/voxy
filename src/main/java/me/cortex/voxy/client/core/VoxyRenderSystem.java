package me.cortex.voxy.client.core;

import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.client.TimingStatistics;
import me.cortex.voxy.client.VoxyClient;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.gl.Capabilities;
import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.GlTexture;
import me.cortex.voxy.client.core.model.ModelBakerySubsystem;
import me.cortex.voxy.client.core.rendering.ChunkBoundRenderer;
import me.cortex.voxy.client.core.rendering.RenderDistanceTracker;
import me.cortex.voxy.client.core.rendering.RenderService;
import me.cortex.voxy.client.core.rendering.building.RenderDataFactory;
import me.cortex.voxy.client.core.rendering.building.RenderGenerationService;
import me.cortex.voxy.client.core.rendering.post.PostProcessing;
import me.cortex.voxy.client.core.rendering.util.DownloadStream;
import me.cortex.voxy.client.core.rendering.util.PrintfDebugUtil;
import me.cortex.voxy.client.core.rendering.util.SharedIndexBuffer;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.thread.ServiceThreadPool;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlBackend;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11.glGetIntegerv;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL14.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;
import static org.lwjgl.opengl.GL33.glBindSampler;

public class VoxyRenderSystem {
    private final RenderService renderer;
    private final PostProcessing postProcessing;
    private final WorldEngine worldIn;
    private final RenderDistanceTracker renderDistanceTracker;
    public final ChunkBoundRenderer chunkBoundRenderer;

    public VoxyRenderSystem(WorldEngine world, ServiceThreadPool threadPool) {
        //Keep the world loaded, NOTE: this is done FIRST, to keep and ensure that even if the rest of loading takes more
        // than timeout, we keep the world acquired
        world.acquireRef();
        try {
            //wait for opengl to be finished, this should hopefully ensure all memory allocations are free
            glFinish();
            glFinish();

            //Trigger the shared index buffer loading
            SharedIndexBuffer.INSTANCE.id();
            Capabilities.init();//Ensure clinit is called

            this.worldIn = world;
            this.renderer = new RenderService(world, threadPool);
            this.postProcessing = new PostProcessing();
            int minSec = MinecraftClient.getInstance().world.getBottomSectionCoord() >> 5;
            int maxSec = (MinecraftClient.getInstance().world.getTopSectionCoord() - 1) >> 5;

            //Do some very cheeky stuff for MiB
            if (false) {
                minSec = -8;
                maxSec = 7;
            }

            this.renderDistanceTracker = new RenderDistanceTracker(20,
                    minSec,
                    maxSec,
                    this.renderer::addTopLevelNode,
                    this.renderer::removeTopLevelNode);

            this.renderDistanceTracker.setRenderDistance(VoxyConfig.CONFIG.sectionRenderDistance);

            this.chunkBoundRenderer = new ChunkBoundRenderer();
        } catch (RuntimeException e) {
            world.releaseRef();//If something goes wrong, we must release the world first
            throw e;
        }
    }

    public void setRenderDistance(int renderDistance) {
        this.renderDistanceTracker.setRenderDistance(renderDistance);
    }


    private void autoBalanceSubDivSize() {
        //only increase quality while there are very few mesh queues, this stops,
        // e.g. while flying and is rendering alot of low quality chunks
        boolean canDecreaseSize = this.renderer.getMeshQueueCount() < 5000;
        float CHANGE_PER_SECOND = 30;
        //Auto fps targeting
        if (MinecraftClient.getInstance().getCurrentFps() < 45) {
            VoxyConfig.CONFIG.subDivisionSize = Math.min(VoxyConfig.CONFIG.subDivisionSize + CHANGE_PER_SECOND / Math.max(1f, MinecraftClient.getInstance().getCurrentFps()), 256);
        }

        if (55 < MinecraftClient.getInstance().getCurrentFps() && canDecreaseSize) {
            VoxyConfig.CONFIG.subDivisionSize = Math.max(VoxyConfig.CONFIG.subDivisionSize - CHANGE_PER_SECOND / Math.max(1f, MinecraftClient.getInstance().getCurrentFps()), 30);
        }
    }

    private static Matrix4f makeProjectionMatrix(float near, float far) {
        //TODO: use the existing projection matrix use mulLocal by the inverse of the projection and then mulLocal our projection

        var projection = new Matrix4f();
        var client = MinecraftClient.getInstance();
        var gameRenderer = client.gameRenderer;//tickCounter.getTickDelta(true);

        float fov = gameRenderer.getFov(gameRenderer.getCamera(), client.getRenderTickCounter().getTickProgress(true), true);

        projection.setPerspective(fov * 0.01745329238474369f,
                (float) client.getWindow().getFramebufferWidth() / (float)client.getWindow().getFramebufferHeight(),
                near, far);
        return projection;
    }

    //TODO: Make a reverse z buffer
    private static Matrix4f computeProjectionMat(Matrix4fc base) {
        return base.mulLocal(
                makeProjectionMatrix(0.05f, MinecraftClient.getInstance().gameRenderer.getFarPlaneDistance()).invert(),
                new Matrix4f()
        ).mulLocal(makeProjectionMatrix(16, 16*3000));
    }

    public void renderOpaque(ChunkRenderMatrices matrices, double cameraX, double cameraY, double cameraZ) {
        if (IrisUtil.irisShadowActive()) {
            return;
        }
        TimingStatistics.resetSamplers();


        //Do some very cheeky stuff for MiB
        if (false) {
            int sector = (((int)Math.floor(cameraX)>>4)+512)>>10;
            cameraX -= sector<<14;//10+4
            cameraY += (16+(256-32-sector*30))*16;
        }

        long startTime = System.nanoTime();
        TimingStatistics.all.start();
        TimingStatistics.main.start();



        int oldFB = GL11.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        int boundFB = oldFB;

        //var target = DefaultTerrainRenderPasses.CUTOUT.getTarget();
        //boundFB = ((net.minecraft.client.texture.GlTexture) target.getColorAttachment()).getOrCreateFramebuffer(((GlBackend) RenderSystem.getDevice()).getFramebufferManager(), target.getDepthAttachment());
        if (boundFB == 0) {
            throw new IllegalStateException("Cannot use the default framebuffer as cannot source from it");
        }

        //this.autoBalanceSubDivSize();

        var projection = computeProjectionMat(matrices.projection());//RenderSystem.getProjectionMatrix();
        //var projection = new Matrix4f(matrices.projection());

        int[] dims = new int[4];
        glGetIntegerv(GL_VIEWPORT, dims);
        var viewport = this.renderer.getViewport();
        viewport
                .setProjection(projection)
                .setModelView(new Matrix4f(matrices.modelView()))
                .setCamera(cameraX, cameraY, cameraZ)
                .setScreenSize(dims[2], dims[3])
                .update();
        viewport.frameId++;

        TimingStatistics.E.start();
        this.chunkBoundRenderer.render(viewport);
        TimingStatistics.E.stop();

        TimingStatistics.F.start();
        this.postProcessing.setup(viewport.width, viewport.height, boundFB);
        TimingStatistics.F.stop();

        this.renderer.renderFarAwayOpaque(viewport, this.chunkBoundRenderer.getDepthBoundTexture());


        TimingStatistics.F.start();
        //Compute the SSAO of the rendered terrain, TODO: fix it breaking depth or breaking _something_ am not sure what
        this.postProcessing.computeSSAO(viewport.MVP);
        TimingStatistics.F.stop();

        TimingStatistics.G.start();
        //We can render the translucent directly after as it is the furthest translucent objects
        this.renderer.renderFarAwayTranslucent(viewport, this.chunkBoundRenderer.getDepthBoundTexture());
        TimingStatistics.G.stop();


        TimingStatistics.F.start();
        this.postProcessing.renderPost(projection, matrices.projection(), boundFB);
        TimingStatistics.F.stop();

        TimingStatistics.main.stop();
        TimingStatistics.postDynamic.start();

        PrintfDebugUtil.tick();

        //As much dynamic runtime stuff here
        {
            //Tick upload stream (this is ok to do here as upload ticking is just memory management)
            UploadStream.INSTANCE.tick();

            while (this.renderDistanceTracker.setCenterAndProcess(cameraX, cameraZ) && VoxyClient.isFrexActive());//While FF is active, run until everything is processed

            //Done here as is allows less gl state resetup
            this.renderer.tickModelService(Math.max(3_000_000-(System.nanoTime()-startTime), 500_000));
        }
        TimingStatistics.postDynamic.stop();

        glBindFramebuffer(GlConst.GL_FRAMEBUFFER, oldFB);

        {//Reset state manager stuffs
            GlStateManager._glBindVertexArray(0);//Clear binding

            GlStateManager._activeTexture(GlConst.GL_TEXTURE0);
            GlStateManager._bindTexture(0);
            glBindSampler(0, 0);

            GlStateManager._activeTexture(GlConst.GL_TEXTURE1);
            GlStateManager._bindTexture(0);
            glBindSampler(1, 0);

            GlStateManager._activeTexture(GlConst.GL_TEXTURE2);
            GlStateManager._bindTexture(0);
            glBindSampler(2, 0);
        }
        TimingStatistics.all.stop();
    }

    public void addDebugInfo(List<String> debug) {
        debug.add("Buf/Tex [#/Mb]: [" + GlBuffer.getCount() + "/" + (GlBuffer.getTotalSize()/1_000_000) + "],[" + GlTexture.getCount() + "/" + (GlTexture.getEstimatedTotalSize()/1_000_000)+"]");
        this.renderer.addDebugData(debug);
        {
            TimingStatistics.update();
            debug.add("Voxy frame runtime (millis): " + TimingStatistics.dynamic.pVal() + ", " + TimingStatistics.main.pVal()+ ", " + TimingStatistics.postDynamic.pVal()+ ", " + TimingStatistics.all.pVal());
            debug.add("Extra time: " + TimingStatistics.A.pVal() + ", " + TimingStatistics.B.pVal() + ", " + TimingStatistics.C.pVal() + ", " + TimingStatistics.D.pVal());
            debug.add("Extra 2 time: " + TimingStatistics.E.pVal() + ", " + TimingStatistics.F.pVal() + ", " + TimingStatistics.G.pVal() + ", " + TimingStatistics.H.pVal() + ", " + TimingStatistics.I.pVal());
        }
        PrintfDebugUtil.addToOut(debug);
    }

    public void shutdown() {
        Logger.info("Flushing download stream");
        DownloadStream.INSTANCE.flushWaitClear();
        Logger.info("Shutting down rendering");
        try {this.renderer.shutdown();this.chunkBoundRenderer.free();} catch (Exception e) {Logger.error("Error shutting down renderer", e);}
        Logger.info("Shutting down post processor");
        if (this.postProcessing!=null){try {this.postProcessing.shutdown();} catch (Exception e) {Logger.error("Error shutting down post processor", e);}}

        //Release hold on the world
        this.worldIn.releaseRef();
    }
}
