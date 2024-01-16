package me.cortex.voxelmon.client.core.rendering;

import me.cortex.voxelmon.client.core.rendering.building.BuiltSectionGeometry;
import me.cortex.voxelmon.client.core.rendering.building.RenderGenerationService;
import me.cortex.voxelmon.common.world.WorldEngine;
import me.cortex.voxelmon.common.world.WorldSection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Direction;

import java.util.concurrent.ConcurrentHashMap;

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
    }

    //Adds a lvl 0 section into the world renderer
    public void addLvl0(int x, int y, int z) {
        this.activeSections.put(WorldEngine.getWorldSectionId(0, x, y, z), O);
        this.renderGen.enqueueTask(0, x, y, z, this::shouldStillBuild, this::getBuildFlagsOrAbort);
    }

    //Removes a lvl 0 section from the world renderer
    public void remLvl0(int x, int y, int z) {
        this.activeSections.remove(WorldEngine.getWorldSectionId(0, x, y, z));
        this.renderer.enqueueResult(new BuiltSectionGeometry(WorldEngine.getWorldSectionId(0, x, y, z), null, null));
        this.renderGen.removeTask(0, x, y, z);
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

        this.renderGen.enqueueTask(lvl, x, y, z, this::shouldStillBuild, this::getBuildFlagsOrAbort);

        this.renderer.enqueueResult(new BuiltSectionGeometry(WorldEngine.getWorldSectionId(lvl-1, (x<<1), (y<<1), (z<<1)), null, null));
        this.renderer.enqueueResult(new BuiltSectionGeometry(WorldEngine.getWorldSectionId(lvl-1, (x<<1), (y<<1), (z<<1)+1), null, null));
        this.renderer.enqueueResult(new BuiltSectionGeometry(WorldEngine.getWorldSectionId(lvl-1, (x<<1), (y<<1)+1, (z<<1)), null, null));
        this.renderer.enqueueResult(new BuiltSectionGeometry(WorldEngine.getWorldSectionId(lvl-1, (x<<1), (y<<1)+1, (z<<1)+1), null, null));
        this.renderer.enqueueResult(new BuiltSectionGeometry(WorldEngine.getWorldSectionId(lvl-1, (x<<1)+1, (y<<1), (z<<1)), null, null));
        this.renderer.enqueueResult(new BuiltSectionGeometry(WorldEngine.getWorldSectionId(lvl-1, (x<<1)+1, (y<<1), (z<<1)+1), null, null));
        this.renderer.enqueueResult(new BuiltSectionGeometry(WorldEngine.getWorldSectionId(lvl-1, (x<<1)+1, (y<<1)+1, (z<<1)), null, null));
        this.renderer.enqueueResult(new BuiltSectionGeometry(WorldEngine.getWorldSectionId(lvl-1, (x<<1)+1, (y<<1)+1, (z<<1)+1), null, null));


        this.renderGen.removeTask(lvl-1, (x<<1), (y<<1), (z<<1));
        this.renderGen.removeTask(lvl-1, (x<<1), (y<<1), (z<<1)+1);
        this.renderGen.removeTask(lvl-1, (x<<1), (y<<1)+1, (z<<1));
        this.renderGen.removeTask(lvl-1, (x<<1), (y<<1)+1, (z<<1)+1);
        this.renderGen.removeTask(lvl-1, (x<<1)+1, (y<<1), (z<<1));
        this.renderGen.removeTask(lvl-1, (x<<1)+1, (y<<1), (z<<1)+1);
        this.renderGen.removeTask(lvl-1, (x<<1)+1, (y<<1)+1, (z<<1));
        this.renderGen.removeTask(lvl-1, (x<<1)+1, (y<<1)+1, (z<<1)+1);
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
        this.renderGen.removeTask(lvl, x, y, z);

        this.renderGen.enqueueTask(lvl - 1, (x<<1), (y<<1), (z<<1), this::shouldStillBuild, this::getBuildFlagsOrAbort);
        this.renderGen.enqueueTask(lvl - 1, (x<<1), (y<<1), (z<<1)+1, this::shouldStillBuild, this::getBuildFlagsOrAbort);
        this.renderGen.enqueueTask(lvl - 1, (x<<1), (y<<1)+1, (z<<1), this::shouldStillBuild, this::getBuildFlagsOrAbort);
        this.renderGen.enqueueTask(lvl - 1, (x<<1), (y<<1)+1, (z<<1)+1, this::shouldStillBuild, this::getBuildFlagsOrAbort);
        this.renderGen.enqueueTask(lvl - 1, (x<<1)+1, (y<<1), (z<<1), this::shouldStillBuild, this::getBuildFlagsOrAbort);
        this.renderGen.enqueueTask(lvl - 1, (x<<1)+1, (y<<1), (z<<1)+1, this::shouldStillBuild, this::getBuildFlagsOrAbort);
        this.renderGen.enqueueTask(lvl - 1, (x<<1)+1, (y<<1)+1, (z<<1), this::shouldStillBuild, this::getBuildFlagsOrAbort);
        this.renderGen.enqueueTask(lvl - 1, (x<<1)+1, (y<<1)+1, (z<<1)+1, this::shouldStillBuild, this::getBuildFlagsOrAbort);

    }

    //Updates a sections direction mask (e.g. if the player goes past the axis, the chunk must be updated)
    public void updateDirMask(int lvl, int x, int y, int z, int newMask) {

    }


    //Called by the world engine when a section gets dirtied
    public void sectionUpdated(WorldSection section) {
        if (this.activeSections.containsKey(section.getKey())) {
            //TODO:FIXME: if the section gets updated, that means that its neighbors might need to be updated aswell
            // (due to block occlusion)

            //TODO: FIXME: REBUILDING THE ENTIRE NEIGHBORS when probably only the internal layout changed is NOT SMART
            this.renderGen.enqueueTask(section.lvl, section.x, section.y, section.z, this::shouldStillBuild, this::getBuildFlagsOrAbort);
            this.renderGen.enqueueTask(section.lvl, section.x-1, section.y, section.z, this::shouldStillBuild, this::getBuildFlagsOrAbort);
            this.renderGen.enqueueTask(section.lvl, section.x+1, section.y, section.z, this::shouldStillBuild, this::getBuildFlagsOrAbort);
            this.renderGen.enqueueTask(section.lvl, section.x, section.y, section.z-1, this::shouldStillBuild, this::getBuildFlagsOrAbort);
            this.renderGen.enqueueTask(section.lvl, section.x, section.y, section.z+1, this::shouldStillBuild, this::getBuildFlagsOrAbort);
        }
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
            buildMask |= ((1<<6)-1)^(1);
        }
        return buildMask;
    }

    public boolean shouldStillBuild(int lvl, int x, int y, int z) {
        return this.activeSections.containsKey(WorldEngine.getWorldSectionId(lvl, x, y, z));
    }
}
