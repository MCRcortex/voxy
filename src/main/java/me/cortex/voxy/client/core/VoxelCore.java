package me.cortex.voxy.client.core;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.client.Voxy;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.rendering.*;
import me.cortex.voxy.client.core.rendering.building.RenderGenerationService;
import me.cortex.voxy.client.core.rendering.post.PostProcessing;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.cortex.voxy.client.saver.ContextSelectionSystem;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.client.importers.WorldImporter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.util.*;

import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING;

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
public class VoxelCore {
    private final WorldEngine world;
    private final DistanceTracker distanceTracker;
    private final RenderGenerationService renderGen;
    private final RenderTracker renderTracker;

    private final AbstractFarWorldRenderer renderer;
    private final ViewportSelector viewportSelector;
    private final PostProcessing postProcessing;

    //private final Thread shutdownThread = new Thread(this::shutdown);

    private WorldImporter importer;
    public VoxelCore(ContextSelectionSystem.Selection worldSelection) {
        this.world = worldSelection.createEngine();
        var cfg = worldSelection.getConfig();
        System.out.println("Initializing voxy core");

        //Trigger the shared index buffer loading
        SharedIndexBuffer.INSTANCE.id();
        Capabilities.init();//Ensure clinit is called
        this.renderer = this.createRenderBackend();
        this.viewportSelector = new ViewportSelector<>(this.renderer::createViewport);
        System.out.println("Renderer initialized");

        this.renderTracker = new RenderTracker(this.world, this.renderer);
        this.renderGen = new RenderGenerationService(this.world, this.renderer.getModelManager(), VoxyConfig.CONFIG.renderThreads, this.renderTracker::processBuildResult, this.renderer.usesMeshlets());
        this.world.setDirtyCallback(this.renderTracker::sectionUpdated);
        this.renderTracker.setRenderGen(this.renderGen);
        System.out.println("Render tracker and generator initialized");

        //To get to chunk scale multiply the scale by 2, the scale is after how many chunks does the lods halve
        int q = VoxyConfig.CONFIG.qualityScale;
        int minY = MinecraftClient.getInstance().world.getBottomSectionCoord()/2;
        int maxY = MinecraftClient.getInstance().world.getTopSectionCoord()/2;

        if (cfg.minYOverride != Integer.MAX_VALUE) {
            minY = cfg.minYOverride;
        }

        if (cfg.maxYOverride != Integer.MIN_VALUE) {
            maxY = cfg.maxYOverride;
        }

        this.distanceTracker = new DistanceTracker(this.renderTracker, new int[]{q,q,q,q},
                (VoxyConfig.CONFIG.renderDistance<0?VoxyConfig.CONFIG.renderDistance:((VoxyConfig.CONFIG.renderDistance+1)/2)),
                minY, maxY);
        System.out.println("Distance tracker initialized");

        this.postProcessing = new PostProcessing();

        this.world.getMapper().setCallbacks(this.renderer::addBlockState, this.renderer::addBiome);


        ////Resave the db incase it failed a recovery
        //this.world.getMapper().forceResaveStates();

        var biomeRegistry = MinecraftClient.getInstance().world.getRegistryManager().get(RegistryKeys.BIOME);
        for (var biome : this.world.getMapper().getBiomeEntries()) {
            //this.renderer.getModelManager().addBiome(biome.id, biomeRegistry.get(new Identifier(biome.biome)));
            this.renderer.addBiome(biome);
        }

        for (var state : this.world.getMapper().getStateEntries()) {
            //this.renderer.getModelManager().addEntry(state.id, state.state);
            this.renderer.addBlockState(state);
        }
        //this.renderer.getModelManager().updateEntry(0, Blocks.GRASS_BLOCK.getDefaultState());

        System.out.println("Voxy core initialized");
    }

    private AbstractFarWorldRenderer<?,?> createRenderBackend() {
        if (false) {
            System.out.println("Using Gl46MeshletFarWorldRendering");
            return new Gl46MeshletsFarWorldRenderer(VoxyConfig.CONFIG.geometryBufferSize, VoxyConfig.CONFIG.maxSections);
        } else {
            if (VoxyConfig.CONFIG.useMeshShaders()) {
                System.out.println("Using NvMeshFarWorldRenderer");
                return new NvMeshFarWorldRenderer(VoxyConfig.CONFIG.geometryBufferSize, VoxyConfig.CONFIG.maxSections);
            } else {
                System.out.println("Using Gl46FarWorldRenderer");
                return new Gl46FarWorldRenderer(VoxyConfig.CONFIG.geometryBufferSize, VoxyConfig.CONFIG.maxSections);
            }
        }
    }


    public void enqueueIngest(WorldChunk worldChunk) {
        this.world.ingestService.enqueueIngest(worldChunk);
    }

    boolean firstTime = true;
    public void renderSetup(Frustum frustum, Camera camera) {
        if (this.firstTime) {
            this.distanceTracker.init(camera.getBlockPos().getX(), camera.getBlockPos().getZ());
            this.firstTime = false;
            //this.renderTracker.addLvl0(0,6,0);
        }
        this.distanceTracker.setCenter(camera.getBlockPos().getX(), camera.getBlockPos().getY(), camera.getBlockPos().getZ());
        this.renderer.setupRender(frustum, camera);
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
        //this.renderer.getModelManager().updateEntry(0, Blocks.DIRT_PATH.getDefaultState());

        //this.renderer.getModelManager().updateEntry(0, Blocks.COMPARATOR.getDefaultState());
        //this.renderer.getModelManager().updateEntry(0, Blocks.OAK_LEAVES.getDefaultState());

        //var fb = Iris.getPipelineManager().getPipelineNullable().getSodiumTerrainPipeline().getTerrainSolidFramebuffer();
        //fb.bind();

        var projection = computeProjectionMat();
        //var projection = RenderSystem.getProjectionMatrix();//computeProjectionMat();
        var viewport = this.viewportSelector.getViewport();
        viewport
                .setProjection(projection)
                .setModelView(matrices.peek().getPositionMatrix())
                .setCamera(cameraX, cameraY, cameraZ)
                .setScreenSize(MinecraftClient.getInstance().getFramebuffer().textureWidth, MinecraftClient.getInstance().getFramebuffer().textureHeight);

        int boundFB = GL11.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
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
        debug.add("I/S/R tasks: " + this.world.ingestService.getTaskCount() + "/"+this.world.savingService.getTaskCount()+"/"+this.renderGen.getTaskCount());
        debug.add("Loaded cache sizes: " + Arrays.toString(this.world.getLoadedSectionCacheSizes()));
        debug.add("Mesh cache count: " + this.renderGen.getMeshCacheCount());
        this.renderer.addDebugData(debug);
    }

    //Note: when doing translucent rendering, only need to sort when generating the geometry, or when crossing into the center zone
    // cause in 99.99% of cases the sections dont need to be sorted
    // since they are AABBS crossing the normal is impossible without one of the axis being equal

    public void shutdown() {
        //if (Thread.currentThread() != this.shutdownThread) {
        //    Runtime.getRuntime().removeShutdownHook(this.shutdownThread);
        //}

        //this.world.getMapper().forceResaveStates();
        if (this.importer != null) {
            System.out.println("Shutting down importer");
            try {this.importer.shutdown();this.importer = null;} catch (Exception e) {System.err.println(e);}
        }
        System.out.println("Shutting down voxel core");
        try {this.renderGen.shutdown();} catch (Exception e) {System.err.println(e);}
        System.out.println("Render gen shut down");
        try {this.world.shutdown();} catch (Exception e) {System.err.println(e);}
        System.out.println("World engine shut down");
        try {this.renderer.shutdown(); this.viewportSelector.free();} catch (Exception e) {System.err.println(e);}
        System.out.println("Renderer shut down");
        if (this.postProcessing!=null){try {this.postProcessing.shutdown();} catch (Exception e) {System.err.println(e);}}
        System.out.println("Voxel core shut down");
    }

    public boolean createWorldImporter(World mcWorld, File worldPath) {
        if (this.importer != null) {
            return false;
        }
        var importer = new WorldImporter(this.world, mcWorld);
        var bossBar = new ClientBossBar(MathHelper.randomUuid(), Text.of("Voxy world importer"), 0.0f, BossBar.Color.GREEN, BossBar.Style.PROGRESS, false, false, false);
        MinecraftClient.getInstance().inGameHud.getBossBarHud().bossBars.put(bossBar.getUuid(), bossBar);
        importer.importWorldAsyncStart(worldPath, 4, (a,b)->
                MinecraftClient.getInstance().executeSync(()-> {
                    bossBar.setPercent(((float) a)/((float) b));
                    bossBar.setName(Text.of("Voxy import: "+ a+"/"+b + " region files"));
                }),
                ()-> {
                    MinecraftClient.getInstance().executeSync(()-> {
                        MinecraftClient.getInstance().inGameHud.getBossBarHud().bossBars.remove(bossBar.getUuid());
                        String msg = "Voxy world import finished";
                        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.literal(msg));
                        System.err.println(msg);
                    });
                    this.importer = null;
                });
        this.importer = importer;
        return true;
    }

    public WorldEngine getWorldEngine() {
        return this.world;
    }
}
