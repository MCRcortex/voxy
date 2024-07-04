package me.cortex.voxy.client.core.rendering;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.building.RenderGenerationService;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Direction;

import java.util.concurrent.ConcurrentHashMap;

//Tracks active sections, dispatches updates to the build system, everything related to rendering flows through here
public class RenderTracker {
    private final WorldEngine world;
    private RenderGenerationService renderGen;
    private final AbstractFarWorldRenderer renderer;
    private final LongSet[] sets;


    public RenderTracker(WorldEngine world, AbstractFarWorldRenderer renderer) {
        this.world = world;
        this.renderer = renderer;
        this.sets = new LongSet[1<<4];
        for (int i = 0; i < this.sets.length; i++) {
            this.sets[i] = new LongOpenHashSet();
        }
    }

    public void setRenderGen(RenderGenerationService renderGen) {
        this.renderGen = renderGen;
    }

    public static long mixStafford13(long seed) {
        seed = (seed ^ seed >>> 30) * -4658895280553007687L;
        seed = (seed ^ seed >>> 27) * -7723592293110705685L;
        return seed ^ seed >>> 31;
    }

    private LongSet getSet(long key) {
        return this.sets[(int) (mixStafford13(key) & (this.sets.length-1))];
    }

    private void put(long key) {
        var set = this.getSet(key);
        synchronized (set) {
            set.add(key);
        }
    }

    private void remove(long key) {
        var set = this.getSet(key);
        synchronized (set) {
            set.remove(key);
        }
    }

    private boolean contains(long key) {
        var set = this.getSet(key);
        synchronized (set) {
            return set.contains(key);
        }
    }

    //TODO: replace this:: with a class cached lambda ref (cause doing this:: still does a lambda allocation)

    //Adds a lvl 0 section into the world renderer
    public void addLvl0(int x, int y, int z) {
        this.put(WorldEngine.getWorldSectionId(0, x, y, z));
        this.renderGen.enqueueTask(0, x, y, z, this::shouldStillBuild);
    }

    //Removes a lvl 0 section from the world renderer
    public void remLvl0(int x, int y, int z) {
        this.remove(WorldEngine.getWorldSectionId(0, x, y, z));
        this.renderer.enqueueResult(new BuiltSection(WorldEngine.getWorldSectionId(0, x, y, z)));
        this.renderGen.removeTask(0, x, y, z);
    }

    //Increases from lvl-1 to lvl at the coordinates (which are in lvl space)
    public void inc(int lvl, int x, int y, int z) {
        this.remove(WorldEngine.getWorldSectionId(lvl-1, (x<<1), (y<<1), (z<<1)));
        this.remove(WorldEngine.getWorldSectionId(lvl-1, (x<<1), (y<<1), (z<<1)+1));
        this.remove(WorldEngine.getWorldSectionId(lvl-1, (x<<1), (y<<1)+1, (z<<1)));
        this.remove(WorldEngine.getWorldSectionId(lvl-1, (x<<1), (y<<1)+1, (z<<1)+1));
        this.remove(WorldEngine.getWorldSectionId(lvl-1, (x<<1)+1, (y<<1), (z<<1)));
        this.remove(WorldEngine.getWorldSectionId(lvl-1, (x<<1)+1, (y<<1), (z<<1)+1));
        this.remove(WorldEngine.getWorldSectionId(lvl-1, (x<<1)+1, (y<<1)+1, (z<<1)));
        this.remove(WorldEngine.getWorldSectionId(lvl-1, (x<<1)+1, (y<<1)+1, (z<<1)+1));
        this.put(WorldEngine.getWorldSectionId(lvl, x, y, z));

        //TODO: make a seperate object to hold the build data and link it with the location in a
        // concurrent hashmap or something, this is so that e.g. the build data position
        // can be updated

        //TODO: replace this:: with a class cached lambda ref (cause doing this:: still does a lambda allocation)
        this.renderGen.enqueueTask(lvl, x, y, z, this::shouldStillBuild);

        this.renderer.enqueueResult(new BuiltSection(WorldEngine.getWorldSectionId(lvl-1, (x<<1), (y<<1), (z<<1))));
        this.renderer.enqueueResult(new BuiltSection(WorldEngine.getWorldSectionId(lvl-1, (x<<1), (y<<1), (z<<1)+1)));
        this.renderer.enqueueResult(new BuiltSection(WorldEngine.getWorldSectionId(lvl-1, (x<<1), (y<<1)+1, (z<<1))));
        this.renderer.enqueueResult(new BuiltSection(WorldEngine.getWorldSectionId(lvl-1, (x<<1), (y<<1)+1, (z<<1)+1)));
        this.renderer.enqueueResult(new BuiltSection(WorldEngine.getWorldSectionId(lvl-1, (x<<1)+1, (y<<1), (z<<1))));
        this.renderer.enqueueResult(new BuiltSection(WorldEngine.getWorldSectionId(lvl-1, (x<<1)+1, (y<<1), (z<<1)+1)));
        this.renderer.enqueueResult(new BuiltSection(WorldEngine.getWorldSectionId(lvl-1, (x<<1)+1, (y<<1)+1, (z<<1))));
        this.renderer.enqueueResult(new BuiltSection(WorldEngine.getWorldSectionId(lvl-1, (x<<1)+1, (y<<1)+1, (z<<1)+1)));


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
        this.put(WorldEngine.getWorldSectionId(lvl-1, (x<<1), (y<<1), (z<<1)));
        this.put(WorldEngine.getWorldSectionId(lvl-1, (x<<1), (y<<1), (z<<1)+1));
        this.put(WorldEngine.getWorldSectionId(lvl-1, (x<<1), (y<<1)+1, (z<<1)));
        this.put(WorldEngine.getWorldSectionId(lvl-1, (x<<1), (y<<1)+1, (z<<1)+1));
        this.put(WorldEngine.getWorldSectionId(lvl-1, (x<<1)+1, (y<<1), (z<<1)));
        this.put(WorldEngine.getWorldSectionId(lvl-1, (x<<1)+1, (y<<1), (z<<1)+1));
        this.put(WorldEngine.getWorldSectionId(lvl-1, (x<<1)+1, (y<<1)+1, (z<<1)));
        this.put(WorldEngine.getWorldSectionId(lvl-1, (x<<1)+1, (y<<1)+1, (z<<1)+1));
        this.remove(WorldEngine.getWorldSectionId(lvl, x, y, z));

        this.renderer.enqueueResult(new BuiltSection(WorldEngine.getWorldSectionId(lvl, x, y, z)));
        this.renderGen.removeTask(lvl, x, y, z);

        //TODO: replace this:: with a class cached lambda ref (cause doing this:: still does a lambda allocation)
        this.renderGen.enqueueTask(lvl - 1, (x<<1), (y<<1), (z<<1), this::shouldStillBuild);
        this.renderGen.enqueueTask(lvl - 1, (x<<1), (y<<1), (z<<1)+1, this::shouldStillBuild);
        this.renderGen.enqueueTask(lvl - 1, (x<<1), (y<<1)+1, (z<<1), this::shouldStillBuild);
        this.renderGen.enqueueTask(lvl - 1, (x<<1), (y<<1)+1, (z<<1)+1, this::shouldStillBuild);
        this.renderGen.enqueueTask(lvl - 1, (x<<1)+1, (y<<1), (z<<1), this::shouldStillBuild);
        this.renderGen.enqueueTask(lvl - 1, (x<<1)+1, (y<<1), (z<<1)+1, this::shouldStillBuild);
        this.renderGen.enqueueTask(lvl - 1, (x<<1)+1, (y<<1)+1, (z<<1), this::shouldStillBuild);
        this.renderGen.enqueueTask(lvl - 1, (x<<1)+1, (y<<1)+1, (z<<1)+1, this::shouldStillBuild);
    }

    //Enqueues a renderTask for a section to cache the result
    public void addCache(int lvl, int x, int y, int z) {
        this.renderGen.markCache(lvl, x, y, z);
        this.renderGen.enqueueTask(lvl, x, y, z, ((lvl1, x1, y1, z1) -> true));//TODO: replace the true identity lambda with a callback check to the render cache
    }

    //Removes the position from the cache
    public void removeCache(int lvl, int x, int y, int z) {
        this.renderGen.unmarkCache(lvl, x, y, z);
    }

    public void remove(int lvl, int x, int y, int z) {
        this.remove(WorldEngine.getWorldSectionId(lvl, x, y, z));
        this.renderer.enqueueResult(new BuiltSection(WorldEngine.getWorldSectionId(lvl, x, y, z)));
    }

    public void add(int lvl, int x, int y, int z) {
        this.put(WorldEngine.getWorldSectionId(lvl, x, y, z));
        //TODO: replace this:: with a class cached lambda ref (cause doing this:: still does a lambda allocation)
        this.renderGen.enqueueTask(lvl, x, y, z, this::shouldStillBuild);
    }


    //Called by the world engine when a section gets dirtied
    public void sectionUpdated(WorldSection section) {
        if (this.contains(section.key)) {
            //TODO:FIXME: if the section gets updated, that means that its neighbors might need to be updated aswell
            // (due to block occlusion)

            //TODO: FIXME: REBUILDING THE ENTIRE NEIGHBORS when probably only the internal layout changed is NOT SMART
            this.renderGen.clearCache(section.lvl, section.x, section.y, section.z);
            this.renderGen.clearCache(section.lvl, section.x-1, section.y, section.z);
            this.renderGen.clearCache(section.lvl, section.x+1, section.y, section.z);
            this.renderGen.clearCache(section.lvl, section.x, section.y, section.z-1);
            this.renderGen.clearCache(section.lvl, section.x, section.y, section.z+1);
            //TODO: replace this:: with a class cached lambda ref (cause doing this:: still does a lambda allocation)
            this.renderGen.enqueueTask(section.lvl, section.x, section.y, section.z, this::shouldStillBuild);
            this.renderGen.enqueueTask(section.lvl, section.x-1, section.y, section.z, this::shouldStillBuild);
            this.renderGen.enqueueTask(section.lvl, section.x+1, section.y, section.z, this::shouldStillBuild);
            this.renderGen.enqueueTask(section.lvl, section.x, section.y, section.z-1, this::shouldStillBuild);
            this.renderGen.enqueueTask(section.lvl, section.x, section.y, section.z+1, this::shouldStillBuild);
        }
        //this.renderGen.enqueueTask(section);
    }

    //called by the RenderGenerationService about built geometry, the RenderTracker checks if it can use the result (e.g. the LoD hasnt changed/still correct etc)
    // and dispatches it to the renderer
    // it also batch collects the geometry sections until all the geometry for an operation is collected, then it executes the operation, its removes flickering
    public void processBuildResult(BuiltSection section) {
        //Check that we still want the section
        if (this.contains(section.position)) {
            this.renderer.enqueueResult(section);
        } else {
            section.free();
        }
    }

    public boolean shouldStillBuild(int lvl, int x, int y, int z) {
        return this.contains(WorldEngine.getWorldSectionId(lvl, x, y, z));
    }
}
