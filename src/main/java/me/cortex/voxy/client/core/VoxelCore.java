package me.cortex.voxy.client.core;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.client.Voxy;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.rendering.*;
import me.cortex.voxy.client.core.rendering.post.PostProcessing;
import me.cortex.voxy.client.core.rendering.util.DownloadStream;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.cortex.voxy.client.saver.ContextSelectionSystem;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.client.importers.WorldImporter;
import me.cortex.voxy.common.world.thread.ServiceThreadPool;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.util.*;

import static org.lwjgl.opengl.ARBDirectStateAccess.glGetNamedFramebufferAttachmentParameteri;
import static org.lwjgl.opengl.GL30C.*;

//Core class that ingests new data from sources and updates the required systems

//3 primary services:
// ingest service: this takes in unloaded chunk events from the client, processes the chunk and critically also updates the lod view of the world
// render data builder service: this service builds the render data from build requests it also handles the collecting of build data for the selected region (only axis aligned single lod tasks)
// serialization service: serializes changed world data and ensures that the database and any loaded data are in sync such that the database can never be more updated than loaded data, also performs compression on serialization

//there are multiple subsystems
//player tracker system (determines what lods are loaded and used by the player)
//updating system (triggers render data rebuilds when something from the ingest service causes an LOD change)
//the render system simply renders what data it has, its responsable for gpu memory layouts in arenas and rendering in an optimal way, it makes no requests back to any of the other systems or services, it just applies render data updates

//There is strict forward only dataflow
//Ingest -> world engine -> raw render data -> render data



//REDESIGN THIS PIECE OF SHIT SPAGETTY SHIT FUCK
// like Get rid of interactor and renderer being seperate just fucking put them together
// fix the callback bullshit spagetti
//REMOVE setRenderGen like holy hell
public class VoxelCore {
    private final WorldEngine world;

    private final RenderService renderer;
    private final PostProcessing postProcessing;
    private final ServiceThreadPool serviceThreadPool;

    private WorldImporter importer;
    public VoxelCore(ContextSelectionSystem.Selection worldSelection) {
        var cfg = worldSelection.getConfig();
        this.serviceThreadPool = new ServiceThreadPool(VoxyConfig.CONFIG.serviceThreads);

        this.world = worldSelection.createEngine(this.serviceThreadPool);
        System.out.println("Initializing voxy core");

        //Trigger the shared index buffer loading
        SharedIndexBuffer.INSTANCE.id();
        Capabilities.init();//Ensure clinit is called

        this.renderer = new RenderService(this.world, this.serviceThreadPool);
        System.out.println("Using " + this.renderer.getClass().getSimpleName());
        this.postProcessing = new PostProcessing();

        System.out.println("Voxy core initialized");
    }

    public void enqueueIngest(WorldChunk worldChunk) {
        this.world.ingestService.enqueueIngest(worldChunk);
    }

    public void renderSetup(Frustum frustum, Camera camera) {
        this.renderer.setup(camera);
        PrintfDebugUtil.tick();
    }

    private static Matrix4f makeProjectionMatrix(float near, float far) {
        //TODO: use the existing projection matrix use mulLocal by the inverse of the projection and then mulLocal our projection

        var projection = new Matrix4f();
        var client = MinecraftClient.getInstance();
        var gameRenderer = client.gameRenderer;//tickCounter.getTickDelta(true);

        float fov = (float) gameRenderer.getFov(gameRenderer.getCamera(), client.getRenderTickCounter().getTickDelta(true), true);

        projection.setPerspective(fov * 0.01745329238474369f,
                (float) client.getWindow().getFramebufferWidth() / (float)client.getWindow().getFramebufferHeight(),
                near, far);
        return projection;
    }

    //TODO: Make a reverse z buffer
    private static Matrix4f computeProjectionMat() {
        return new Matrix4f(RenderSystem.getProjectionMatrix()).mulLocal(
                makeProjectionMatrix(0.05f, MinecraftClient.getInstance().gameRenderer.getFarPlaneDistance()).invert()
        ).mulLocal(makeProjectionMatrix(16, 16*3000));
    }

    public void renderOpaque(MatrixStack matrices, double cameraX, double cameraY, double cameraZ) {
        if (IrisUtil.irisShadowActive()) {
            return;
        }
        matrices.push();
        matrices.translate(-cameraX, -cameraY, -cameraZ);
        matrices.pop();

        var projection = computeProjectionMat();//RenderSystem.getProjectionMatrix();

        var viewport = this.renderer.getViewport();
        viewport
                .setProjection(projection)
                .setModelView(matrices.peek().getPositionMatrix())
                .setCamera(cameraX, cameraY, cameraZ)
                .setScreenSize(MinecraftClient.getInstance().getFramebuffer().textureWidth, MinecraftClient.getInstance().getFramebuffer().textureHeight);
        viewport.frameId++;

        int boundFB = GL11.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        if (boundFB == 0) {
            throw new IllegalStateException("Cannot use the default framebuffer as cannot source from it");
        }
        //TODO: use the raw depth buffer texture instead
        //int boundDepthBuffer = glGetNamedFramebufferAttachmentParameteri(boundFB, GL_DEPTH_STENCIL_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        this.postProcessing.setup(MinecraftClient.getInstance().getFramebuffer().textureWidth, MinecraftClient.getInstance().getFramebuffer().textureHeight, boundFB);

        this.renderer.renderFarAwayOpaque(viewport);

        //Compute the SSAO of the rendered terrain
        this.postProcessing.computeSSAO(projection, matrices);

        //We can render the translucent directly after as it is the furthest translucent objects
        this.renderer.renderFarAwayTranslucent(viewport);


        this.postProcessing.renderPost(projection, RenderSystem.getProjectionMatrix(), boundFB);

    }

    public void addDebugInfo(List<String> debug) {
        debug.add("");
        debug.add("");
        debug.add("Voxy Core: " + Voxy.VERSION);
        /*
        debug.add("Ingest service tasks: " + this.world.ingestService.getTaskCount());
        debug.add("Saving service tasks: " + this.world.savingService.getTaskCount());
        debug.add("Render service tasks: " + this.renderGen.getTaskCount());
         */
        debug.add("I/S tasks: " + this.world.ingestService.getTaskCount() + "/"+this.world.savingService.getTaskCount());
        debug.add("SCS: " + Arrays.toString(this.world.getLoadedSectionCacheSizes()));
        this.renderer.addDebugData(debug);

        PrintfDebugUtil.addToOut(debug);
    }

    //Note: when doing translucent rendering, only need to sort when generating the geometry, or when crossing into the center zone
    // cause in 99.99% of cases the sections dont need to be sorted
    // since they are AABBS crossing the normal is impossible without one of the axis being equal

    public void shutdown() {
        System.out.println("Flushing download stream");
        DownloadStream.INSTANCE.flushWaitClear();

        //if (Thread.currentThread() != this.shutdownThread) {
        //    Runtime.getRuntime().removeShutdownHook(this.shutdownThread);
        //}

        //this.world.getMapper().forceResaveStates();
        if (this.importer != null) {
            System.out.println("Shutting down importer");
            try {this.importer.shutdown();this.importer = null;} catch (Exception e) {e.printStackTrace();}
        }
        System.out.println("Shutting down rendering");
        try {this.renderer.shutdown();} catch (Exception e) {e.printStackTrace();}
        System.out.println("Shutting down post processor");
        if (this.postProcessing!=null){try {this.postProcessing.shutdown();} catch (Exception e) {e.printStackTrace();}}
        System.out.println("Shutting down world engine");
        try {this.world.shutdown();} catch (Exception e) {e.printStackTrace();}
        System.out.println("Shutting down service thread pool");
        this.serviceThreadPool.shutdown();
        System.out.println("Voxel core shut down");
    }

    public boolean createWorldImporter(World mcWorld, File worldPath) {
        if (this.importer != null) {
            this.importer = new WorldImporter(this.world, mcWorld, this.serviceThreadPool);
        }
        if (this.importer.isBusy()) {
            return false;
        }

        var bossBar = new ClientBossBar(MathHelper.randomUuid(), Text.of("Voxy world importer"), 0.0f, BossBar.Color.GREEN, BossBar.Style.PROGRESS, false, false, false);
        MinecraftClient.getInstance().inGameHud.getBossBarHud().bossBars.put(bossBar.getUuid(), bossBar);
        this.importer.importWorldAsyncStart(worldPath, (a,b)->
                MinecraftClient.getInstance().executeSync(()-> {
                    bossBar.setPercent(((float) a)/((float) b));
                    bossBar.setName(Text.of("Voxy import: "+ a+"/"+b + " chunks"));
                }),
                ()-> {
                    MinecraftClient.getInstance().executeSync(()-> {
                        MinecraftClient.getInstance().inGameHud.getBossBarHud().bossBars.remove(bossBar.getUuid());
                        String msg = "Voxy world import finished";
                        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.literal(msg));
                        System.err.println(msg);
                    });
                });
        return true;
    }

    public WorldEngine getWorldEngine() {
        return this.world;
    }
}
