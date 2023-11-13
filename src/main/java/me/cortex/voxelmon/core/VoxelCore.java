package me.cortex.voxelmon.core;

import me.cortex.voxelmon.core.rendering.AbstractFarWorldRenderer;
import me.cortex.voxelmon.core.rendering.Gl46FarWorldRenderer;
import me.cortex.voxelmon.core.rendering.RenderTracker;
import me.cortex.voxelmon.core.rendering.SharedIndexBuffer;
import me.cortex.voxelmon.core.rendering.building.BuiltSectionGeometry;
import me.cortex.voxelmon.core.rendering.building.RenderGenerationService;
import me.cortex.voxelmon.core.util.DebugUtil;
import me.cortex.voxelmon.core.util.MemoryBuffer;
import me.cortex.voxelmon.core.util.RingUtil;
import me.cortex.voxelmon.core.world.WorldEngine;
import me.cortex.voxelmon.core.world.WorldSection;
import me.cortex.voxelmon.core.world.other.BiomeColour;
import me.cortex.voxelmon.core.world.other.BlockStateColour;
import me.cortex.voxelmon.core.world.other.ColourResolver;
import me.cortex.voxelmon.importers.WorldImporter;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.WorldChunk;
import org.lwjgl.system.MemoryUtil;

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
    public static VoxelCore INSTANCE = new VoxelCore();

    private final WorldEngine world;
    private final DistanceTracker distanceTracker;
    private final RenderGenerationService renderGen;
    private final RenderTracker renderTracker;
    private final AbstractFarWorldRenderer renderer;


    public VoxelCore() {
        //Trigger the shared index buffer loading
        SharedIndexBuffer.INSTANCE.id();
        this.renderer = new Gl46FarWorldRenderer();
        this.world = new WorldEngine(new File("ethoslab.db"), 16, 5);//"hc9.db"//"storagefile.db"

        this.renderTracker = new RenderTracker(this.world, this.renderer);
        this.renderGen = new RenderGenerationService(this.world, this.renderTracker,4);
        this.world.setRenderTracker(this.renderTracker);
        this.renderTracker.setRenderGen(this.renderGen);

        this.distanceTracker = new DistanceTracker(this.renderTracker, 5);

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));



        /*
        Random r = new Random();
        for (int ring = 0; ring < 5; ring++) {
            for (int x = -32; x < 32; x++) {
                for (int z = -32; z < 32; z++) {
                    if ((-16 < x && x < 16) && (-16 < z && z < 16)) {
                        continue;
                    }
                    var b = new MemoryBuffer(1000 * 8);
                    for (long j = 0; j < b.size; j += 8) {
                        MemoryUtil.memPutLong(b.address + j, r.nextLong());
                    }
                    this.renderer.enqueueResult(new BuiltSectionGeometry(WorldEngine.getWorldSectionId(ring, x, 2>>ring, z), b, null));
                }
            }
        }*/



        //WorldImporter importer = new WorldImporter(this.world, MinecraftClient.getInstance().world);
        //importer.importWorldAsyncStart(new File("saves/Etho's LP Ep550/region"));

        Set<Block> biomeTintableAllFaces = new HashSet<>(List.of(Blocks.OAK_LEAVES, Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES, Blocks.VINE, Blocks.MANGROVE_LEAVES,
                Blocks.TALL_GRASS, Blocks.LARGE_FERN));

        biomeTintableAllFaces.add(Blocks.SPRUCE_LEAVES);
        biomeTintableAllFaces.add(Blocks.BIRCH_LEAVES);
        biomeTintableAllFaces.add(Blocks.PINK_PETALS);
        biomeTintableAllFaces.addAll(List.of(Blocks.FERN, Blocks.GRASS, Blocks.POTTED_FERN));
        Set<Block> biomeTintableUpFace = new HashSet<>(List.of(Blocks.GRASS_BLOCK));

        Set<Block> waterTint = new HashSet<>(List.of(Blocks.WATER));

        int i = 0;
        for (var state : this.world.getMapper().getBlockStates()) {
            int tintMsk = 0;
            if (biomeTintableAllFaces.contains(state.getBlock())) {
                tintMsk |= (1<<6)-1;
            }
            if (biomeTintableUpFace.contains(state.getBlock())) {
                tintMsk |= 1<<Direction.UP.getId();
            }
            if (waterTint.contains(state.getBlock())) {
                tintMsk |= 1<<6;
            }
            this.renderer.enqueueUpdate(new BlockStateColour(i++, tintMsk, ColourResolver.resolveColour(state)));
        }

        i = 0;
        for (var biome : this.world.getMapper().getBiomes()) {
            long dualColour = ColourResolver.resolveBiomeColour(biome);
            this.renderer.enqueueUpdate(new BiomeColour(i++, (int) dualColour, (int) (dualColour>>32)));
        }
    }

    public void enqueueIngest(WorldChunk worldChunk) {
        this.world.ingestService.enqueueIngest(worldChunk);
    }

    public void renderSetup(Frustum frustum, Camera camera) {
        this.distanceTracker.setCenter(camera.getBlockPos().getX(), camera.getBlockPos().getY(), camera.getBlockPos().getZ());
        this.renderer.setupRender(frustum, camera);
    }

    public void renderOpaque(MatrixStack matrices, double cameraX, double cameraY, double cameraZ) {
        matrices.push();
        matrices.translate(-cameraX, -cameraY, -cameraZ);
        DebugUtil.setPositionMatrix(matrices);
        matrices.pop();

        /*
        for (int i = 0; i < 5; i++) {
            for (int y = 0; y < Math.max(1, 10>>i); y++) {
                for (int x = -32; x < 32; x++) {
                    for (int z = -32; z < 32; z++) {
                        if (-16 < x && x < 16 && -16 < z && z < 16) {
                            continue;
                        }
                        var sec = this.world.getOrLoadAcquire(i, x, y, z);
                        this.renderGen.enqueueTask(sec);
                        sec.release();
                    }
                }
            }
        }*/


        //DebugRenderUtil.renderAABB(new Box(0,100,0,1,101,1), 0,1,0,0.1f);
        //DebugRenderUtil.renderAABB(new Box(1,100,1,2,101,2), 1,0,0,0.1f);


        /*
        int LEVEL = 4;
        int SEC_Y = 1>>LEVEL;
        int X = 47>>LEVEL;
        int Z = 32>>LEVEL;
        var section = world.getOrLoadAcquire(LEVEL,X,SEC_Y,Z);
        var data = section.copyData();
        int SCALE = 1<<LEVEL;
        int Y_OFFSET = SEC_Y<<(5+LEVEL);
        int X_OFFSET = X<<(5+LEVEL);
        int Z_OFFSET = Z<<(5+LEVEL);
        for (int y = 0; y < 32; y++) {
            for (int z = 0; z < 32; z++) {
                for (int x = 0; x < 32; x++) {
                    var point = data[WorldSection.getIndex(x,y,z)];
                    if (point != 0) {
                        //var colours = world.getMapper().getColours(point);
                        //int colour =  colours[Direction.UP.getId()];
                        //DebugUtil.renderAABB(new Box(x*SCALE,y*SCALE+Y_OFFSET,z*SCALE,x*SCALE+SCALE,y*SCALE+SCALE+Y_OFFSET,z*SCALE+SCALE), colour|0xFF);
                        point >>>= 27;
                        DebugUtil.renderAABB(new Box(x*SCALE + X_OFFSET,y*SCALE+Y_OFFSET,z*SCALE+Z_OFFSET,x*SCALE+SCALE + X_OFFSET,y*SCALE+SCALE+Y_OFFSET,z*SCALE+SCALE+Z_OFFSET), (float) (point&7)/7,(float) ((point>>3)&7)/7,(float) ((point>>6)&7)/7,1f);
                    }
                }
            }
        }
        section.release();
         */


        /*
        var points = RingUtil.generatingBoundingCorner2D(4);
        for (var point : points) {
            int x = point>>>16;
            int y = point&0xFFFF;
            DebugUtil.renderAABB(new Box(x,150,y,x+1,151,y+1), 1,1,0,1);
        }
        */

        this.renderer.renderFarAwayOpaque(matrices, cameraX, cameraY, cameraZ);
    }

    public void addDebugInfo(List<String> debug) {
        debug.add("");
        debug.add("");
        debug.add("VoxelCore");
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
        try {this.renderGen.shutdown();} catch (Exception e) {System.err.println(e);}
        try {this.renderer.shutdown();} catch (Exception e) {System.err.println(e);}
        try {this.world.shutdown();} catch (Exception e) {System.err.println(e);}
    }
}
