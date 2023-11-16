package me.cortex.voxelmon.core.rendering;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.cortex.voxelmon.core.rendering.building.BuiltSectionGeometry;
import me.cortex.voxelmon.core.rendering.building.RenderGenerationService;
import me.cortex.voxelmon.core.world.WorldEngine;
import me.cortex.voxelmon.core.world.WorldSection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Direction;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

//Tracks active sections, dispatches updates to the build system, everything related to rendering flows through here
public class RenderTracker {
    private static final class ActiveSectionObject {
        private int buildFlags;
    }
    private final WorldEngine world;
    private RenderGenerationService renderGen;
    private final AbstractFarWorldRenderer renderer;

    //private final Long2ObjectOpenHashMap<Object> activeSections = new Long2ObjectOpenHashMap<>();
    private final ConcurrentHashMap<Long,Object> activeSections = new ConcurrentHashMap<>(50000,0.75f, 16);
    private static final Object O = new Object();


    public void setRenderGen(RenderGenerationService renderGen) {
        this.renderGen = renderGen;
    }
    public RenderTracker(WorldEngine world, AbstractFarWorldRenderer renderer) {
        this.world = world;
        this.renderer = renderer;



        var loader = new Thread(()->{
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            int OX = 0;//-27;
            int OZ = 0;//276;
            int DROP = 48;

            //Do ring rendering
            for (int i = 0; i < 5; i++) {
                for (int x = -DROP; x <= DROP; x++) {
                    for (int z = -DROP; z <= DROP; z++) {
                        int d = x*x+z*z;
                        if (d<(DROP/2-1)*(DROP/2) || d>DROP*DROP)
                            continue;

                        for (int y = -3>>i; y < Math.max(1, 10 >> i); y++) {
                            var sec = this.world.acquire(i, x + (OX>>(1+i)), y, z + (OZ>>(1+i)));
                            //this.renderGen.enqueueTask(sec);
                            sec.release();
                        }

                        try {
                            while (this.renderGen.getTaskCount() > 1000) {
                                Thread.sleep(50);
                            }
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        });
        loader.setDaemon(true);
        //loader.start();
    }

    //Adds a lvl 0 section into the world renderer
    public void addLvl0(int x, int y, int z) {
        this.renderGen.enqueueTask(0, x, y, z);
        this.activeSections.put(WorldEngine.getWorldSectionId(0, x, y, z), O);
    }

    //Removes a lvl 0 section from the world renderer
    public void remLvl0(int x, int y, int z) {
        this.activeSections.remove(WorldEngine.getWorldSectionId(0, x, y, z));
        this.renderer.enqueueResult(new BuiltSectionGeometry(WorldEngine.getWorldSectionId(0, x, y, z), null, null));
    }

    //Increases from lvl-1 to lvl at the coordinates (which are in lvl space)
    public void inc(int lvl, int x, int y, int z) {
        this.activeSections.remove(WorldEngine.getWorldSectionId(lvl-1, (x<<1), (y<<1), (z<<1)));
        this.activeSections.remove(WorldEngine.getWorldSectionId(lvl-1, (x<<1), (y<<1), (z<<1)+1));
        this.activeSections.remove(WorldEngine.getWorldSectionId(lvl-1, (x<<1), (y<<1)+1, (z<<1)));
        this.activeSections.remove(WorldEngine.getWorldSectionId(lvl-1, (x<<1), (y<<1)+1, (z<<1)+1));
        this.activeSections.remove(WorldEngine.getWorldSectionId(lvl-1, (x<<1)+1, (y<<1), (z<<1)));
        this.activeSections.remove(WorldEngine.getWorldSectionId(lvl-1, (x<<1)+1, (y<<1), (z<<1)+1));
        this.activeSections.remove(WorldEngine.getWorldSectionId(lvl-1, (x<<1)+1, (y<<1)+1, (z<<1)));
        this.activeSections.remove(WorldEngine.getWorldSectionId(lvl-1, (x<<1)+1, (y<<1)+1, (z<<1)+1));
        this.activeSections.put(WorldEngine.getWorldSectionId(lvl, x, y, z), O);

        //TODO: make a seperate object to hold the build data and link it with the location in a
        // concurrent hashmap or something, this is so that e.g. the build data position
        // can be updated

        this.renderGen.enqueueTask(lvl, x, y, z);

        this.renderer.enqueueResult(new BuiltSectionGeometry(WorldEngine.getWorldSectionId(lvl-1, (x<<1), (y<<1), (z<<1)), null, null));
        this.renderer.enqueueResult(new BuiltSectionGeometry(WorldEngine.getWorldSectionId(lvl-1, (x<<1), (y<<1), (z<<1)+1), null, null));
        this.renderer.enqueueResult(new BuiltSectionGeometry(WorldEngine.getWorldSectionId(lvl-1, (x<<1), (y<<1)+1, (z<<1)), null, null));
        this.renderer.enqueueResult(new BuiltSectionGeometry(WorldEngine.getWorldSectionId(lvl-1, (x<<1), (y<<1)+1, (z<<1)+1), null, null));
        this.renderer.enqueueResult(new BuiltSectionGeometry(WorldEngine.getWorldSectionId(lvl-1, (x<<1)+1, (y<<1), (z<<1)), null, null));
        this.renderer.enqueueResult(new BuiltSectionGeometry(WorldEngine.getWorldSectionId(lvl-1, (x<<1)+1, (y<<1), (z<<1)+1), null, null));
        this.renderer.enqueueResult(new BuiltSectionGeometry(WorldEngine.getWorldSectionId(lvl-1, (x<<1)+1, (y<<1)+1, (z<<1)), null, null));
        this.renderer.enqueueResult(new BuiltSectionGeometry(WorldEngine.getWorldSectionId(lvl-1, (x<<1)+1, (y<<1)+1, (z<<1)+1), null, null));




    }

    //Decreases from lvl to lvl-1 at the coordinates (which are in lvl space)
    public void dec(int lvl, int x, int y, int z) {
        this.activeSections.put(WorldEngine.getWorldSectionId(lvl-1, (x<<1), (y<<1), (z<<1)), O);
        this.activeSections.put(WorldEngine.getWorldSectionId(lvl-1, (x<<1), (y<<1), (z<<1)+1), O);
        this.activeSections.put(WorldEngine.getWorldSectionId(lvl-1, (x<<1), (y<<1)+1, (z<<1)), O);
        this.activeSections.put(WorldEngine.getWorldSectionId(lvl-1, (x<<1), (y<<1)+1, (z<<1)+1), O);
        this.activeSections.put(WorldEngine.getWorldSectionId(lvl-1, (x<<1)+1, (y<<1), (z<<1)), O);
        this.activeSections.put(WorldEngine.getWorldSectionId(lvl-1, (x<<1)+1, (y<<1), (z<<1)+1), O);
        this.activeSections.put(WorldEngine.getWorldSectionId(lvl-1, (x<<1)+1, (y<<1)+1, (z<<1)), O);
        this.activeSections.put(WorldEngine.getWorldSectionId(lvl-1, (x<<1)+1, (y<<1)+1, (z<<1)+1), O);
        this.activeSections.remove(WorldEngine.getWorldSectionId(lvl, x, y, z));

        this.renderer.enqueueResult(new BuiltSectionGeometry(lvl, x, y, z, null, null));

        this.renderGen.enqueueTask(lvl - 1, (x<<1), (y<<1), (z<<1));
        this.renderGen.enqueueTask(lvl - 1, (x<<1), (y<<1), (z<<1)+1);
        this.renderGen.enqueueTask(lvl - 1, (x<<1), (y<<1)+1, (z<<1));
        this.renderGen.enqueueTask(lvl - 1, (x<<1), (y<<1)+1, (z<<1)+1);
        this.renderGen.enqueueTask(lvl - 1, (x<<1)+1, (y<<1), (z<<1));
        this.renderGen.enqueueTask(lvl - 1, (x<<1)+1, (y<<1), (z<<1)+1);
        this.renderGen.enqueueTask(lvl - 1, (x<<1)+1, (y<<1)+1, (z<<1));
        this.renderGen.enqueueTask(lvl - 1, (x<<1)+1, (y<<1)+1, (z<<1)+1);

    }

    //Updates a sections direction mask (e.g. if the player goes past the axis, the chunk must be updated)
    public void updateDirMask(int lvl, int x, int y, int z, int newMask) {

    }


    //Called by the world engine when a section gets dirtied
    public void sectionUpdated(WorldSection section) {
        //this.renderGen.enqueueTask(section);
    }

    //called by the RenderGenerationService about built geometry, the RenderTracker checks if it can use the result (e.g. the LoD hasnt changed/still correct etc)
    // and dispatches it to the renderer
    // it also batch collects the geometry sections until all the geometry for an operation is collected, then it executes the operation, its removes flickering
    public void processBuildResult(BuiltSectionGeometry section) {
        //Check that we still want the section
        if (this.activeSections.containsKey(section.position)) {
            this.renderer.enqueueResult(section);
        } else {
            section.free();
        }
    }

    public int getBuildFlagsOrAbort(WorldSection section) {
        var cam = MinecraftClient.getInstance().cameraEntity;
        if (cam == null) {
            return 0;
        }
        var holder = this.activeSections.get(section.getKey());
        int buildMask = 0;
        if (holder != null) {
            if (section.z<(((int)cam.getPos().z)>>(5+section.lvl))+1) {
                buildMask |= 1<< Direction.SOUTH.getId();
            }
            if (section.z>(((int)cam.getPos().z)>>(5+section.lvl))-1) {
                buildMask |= 1<<Direction.NORTH.getId();
            }
            if (section.x<(((int)cam.getPos().x)>>(5+section.lvl))+1) {
                buildMask |= 1<<Direction.EAST.getId();
            }
            if (section.x>(((int)cam.getPos().x)>>(5+section.lvl))-1) {
                buildMask |= 1<<Direction.WEST.getId();
            }
            buildMask |= 1<<Direction.UP.getId();
            //buildMask |= ((1<<6)-1)^(1);
        }
        return buildMask;
    }

    public boolean shouldStillBuild(int lvl, int x, int y, int z) {
        return this.activeSections.containsKey(WorldEngine.getWorldSectionId(lvl, x, y, z));
    }
}
