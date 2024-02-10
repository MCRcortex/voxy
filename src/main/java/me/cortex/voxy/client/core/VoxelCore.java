package me.cortex.voxy.client.core;

import com.mojang.blaze3d.platform.GlStateManager;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.rendering.*;
import me.cortex.voxy.client.core.rendering.building.RenderGenerationService;
import me.cortex.voxy.client.core.rendering.post.PostProcessing;
import me.cortex.voxy.client.core.util.DebugUtil;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.client.importers.WorldImporter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;

import java.io.File;
import java.util.*;

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
    private final PostProcessing postProcessing;

    //private final Thread shutdownThread = new Thread(this::shutdown);

    public VoxelCore(WorldEngine engine) {
        this.world = engine;
        System.out.println("Initializing voxy core");

        //Trigger the shared index buffer loading
        SharedIndexBuffer.INSTANCE.id();
        this.renderer = new Gl46FarWorldRenderer(VoxyConfig.CONFIG.geometryBufferSize, VoxyConfig.CONFIG.maxSections);
        System.out.println("Renderer initialized");

        this.renderTracker = new RenderTracker(this.world, this.renderer);
        this.renderGen = new RenderGenerationService(this.world, this.renderer.getModelManager(), VoxyConfig.CONFIG.renderThreads, this.renderTracker::processBuildResult);
        this.world.setDirtyCallback(this.renderTracker::sectionUpdated);
        this.renderTracker.setRenderGen(this.renderGen);
        System.out.println("Render tracker and generator initialized");

        //To get to chunk scale multiply the scale by 2, the scale is after how many chunks does the lods halve
        int q = VoxyConfig.CONFIG.qualityScale;
        //TODO: add an option for cache load and unload distance
        this.distanceTracker = new DistanceTracker(this.renderTracker, new int[]{q,q,q,q}, 8, 16);
        System.out.println("Distance tracker initialized");

        this.postProcessing = new PostProcessing();

        this.world.getMapper().setCallbacks(this.renderer::addBlockState, this.renderer::addBiome);


        ////Resave the db incase it failed a recovery
        //this.world.getMapper().forceResaveStates();

        var biomeRegistry = MinecraftClient.getInstance().world.getRegistryManager().get(RegistryKeys.BIOME);
        for (var biome : this.world.getMapper().getBiomeEntries()) {
            this.renderer.getModelManager().addBiome(biome.id, biomeRegistry.get(new Identifier(biome.biome)));
        }

        for (var state : this.world.getMapper().getStateEntries()) {
            this.renderer.getModelManager().addEntry(state.id, state.state);
        }
        //this.renderer.getModelManager().updateEntry(0, Blocks.GRASS_BLOCK.getDefaultState());

        System.out.println("Voxy core initialized");
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

    private Matrix4f getProjectionMatrix() {

        var projection = new Matrix4f();
        var client = MinecraftClient.getInstance();
        var gameRenderer = client.gameRenderer;

        float fov = (float) gameRenderer.getFov(gameRenderer.getCamera(), client.getTickDelta(), true);

        projection.setPerspective(fov * 0.01745329238474369f,
                (float) client.getWindow().getFramebufferWidth() / (float)client.getWindow().getFramebufferHeight(),
                64F, 16 * 3000f);
        var transform = new Matrix4f().identity();
        transform.translate(gameRenderer.zoomX, -gameRenderer.zoomY, 0.0F);
        transform.scale(gameRenderer.zoom, gameRenderer.zoom, 1.0F);
        return transform.mul(projection);
    }

    public void renderOpaque(MatrixStack matrices, double cameraX, double cameraY, double cameraZ) {
        matrices.push();
        matrices.translate(-cameraX, -cameraY, -cameraZ);
        DebugUtil.setPositionMatrix(matrices);
        matrices.pop();
        //this.renderer.getModelManager().updateEntry(0, Blocks.DIRT_PATH.getDefaultState());

        //this.renderer.getModelManager().updateEntry(0, Blocks.COMPARATOR.getDefaultState());
        //this.renderer.getModelManager().updateEntry(0, Blocks.OAK_LEAVES.getDefaultState());

        int boundFB = GlStateManager.getBoundFramebuffer();
        this.postProcessing.setup(MinecraftClient.getInstance().getFramebuffer().textureWidth, MinecraftClient.getInstance().getFramebuffer().textureHeight, boundFB);

        //TODO: FIXME: since we just bound the post processing FB the depth information isnt
        // copied over, we must do this manually and also copy it with respect to the
        // near/far planes


        //TODO: have the renderer also render a bounding full face just like black boarders around lvl 0
        // this is cause the terrain might not exist and so all the caves are visible causing hell for the
        // occlusion culler

        var projection = this.getProjectionMatrix();

        this.renderer.renderFarAwayOpaque(projection, matrices, cameraX, cameraY, cameraZ);

        //Compute the SSAO of the rendered terrain
        this.postProcessing.computeSSAO(projection, matrices);

        //We can render the translucent directly after as it is the furthest translucent objects
        this.renderer.renderFarAwayTranslucent();


        this.postProcessing.renderPost(boundFB);

    }

    public void addDebugInfo(List<String> debug) {
        debug.add("");
        debug.add("");
        debug.add("Voxy Core");
        debug.add("Ingest service tasks: " + this.world.ingestService.getTaskCount());
        debug.add("Saving service tasks: " + this.world.savingService.getTaskCount());
        debug.add("Render service tasks: " + this.renderGen.getTaskCount());
        debug.add("Loaded cache sizes: " + Arrays.toString(this.world.getLoadedSectionCacheSizes()));
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
        System.out.println("Shutting down voxel core");
        try {this.renderGen.shutdown();} catch (Exception e) {System.err.println(e);}
        System.out.println("Render gen shut down");
        try {this.world.shutdown();} catch (Exception e) {System.err.println(e);}
        System.out.println("World engine shut down");
        try {this.renderer.shutdown();} catch (Exception e) {System.err.println(e);}
        System.out.println("Renderer shut down");
        if (this.postProcessing!=null){try {this.postProcessing.shutdown();} catch (Exception e) {System.err.println(e);}}
        System.out.println("Voxel core shut down");
    }

    public WorldImporter createWorldImporter(World mcWorld, File worldPath) {
        var importer = new WorldImporter(this.world, mcWorld);
        importer.importWorldAsyncStart(worldPath, 15, null, ()->{
            System.err.println("DONE IMPORT");
        });
        return importer;
    }
}
