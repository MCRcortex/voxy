package me.cortex.voxelmon.core.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.cortex.voxelmon.core.rendering.RenderTracker;
import me.cortex.voxelmon.core.voxelization.VoxelizedSection;
import me.cortex.voxelmon.core.world.other.Mapper;
import me.cortex.voxelmon.core.world.service.SectionSavingService;
import me.cortex.voxelmon.core.world.service.VoxelIngestService;
import me.cortex.voxelmon.core.world.storage.StorageBackend;

import java.io.File;
import java.util.Arrays;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

//Use an LMDB backend to store the world, use a local inmemory cache for lod sections
// automatically manages and invalidates sections of the world as needed
public class WorldEngine {
    private static final int ACTIVE_CACHE_SIZE = 10;

    public final StorageBackend storage;
    private final Mapper mapper;
    private final ActiveSectionTracker sectionTracker;
    public final VoxelIngestService ingestService = new VoxelIngestService(this);
    public final SectionSavingService savingService;
    private RenderTracker renderTracker;

    public void setRenderTracker(RenderTracker tracker) {
        this.renderTracker = tracker;
    }

    public Mapper getMapper() {return this.mapper;}

    private final int maxMipLevels;


    public WorldEngine(File storagePath, int savingServiceWorkers, int maxMipLayers) {
        this.maxMipLevels = maxMipLayers;
        this.storage = new StorageBackend(storagePath);
        this.mapper = new Mapper(this.storage);
        this.sectionTracker = new ActiveSectionTracker(maxMipLayers, this::unsafeLoadSection);

        this.savingService = new SectionSavingService(this, savingServiceWorkers);
    }

    private boolean unsafeLoadSection(WorldSection into) {
        var data = this.storage.getSectionData(into.getKey());
        if (data != null) {
            if (!SaveLoadSystem.deserialize(into, data)) {
                this.storage.deleteSectionData(into.getKey());
                //TODO: regenerate the section from children
                Arrays.fill(into.data, Mapper.AIR);
                System.err.println("Section " + into.lvl + ", " + into.x + ", " + into.y + ", " + into.z + " was unable to load, setting to air");
                return true;
            }
        }
        return true;
    }

    public WorldSection acquire(int lvl, int x, int y, int z) {
        return this.sectionTracker.acquire(lvl, x, y, z);
    }

    //TODO: Fixme/optimize, cause as the lvl gets higher, the size of x,y,z gets smaller so i can dynamically compact the format
    // depending on the lvl, which should optimize colisions and whatnot
    public static long getWorldSectionId(int lvl, int x, int y, int z) {
        return ((long)lvl<<60)|((long)(y&0xFF)<<52)|((long)(z&((1<<24)-1))<<28)|((long)(x&((1<<24)-1))<<4);//NOTE: 4 bits spare for whatever
    }

    //Marks a section as dirty, enqueuing it for saving and or render data rebuilding
    private void markDirty(WorldSection section) {
        this.renderTracker.sectionUpdated(section);
        //TODO: add an option for having synced saving, that is when call enqueueSave, that will instead, instantly
        // save to the db, this can be useful for just reducing the amount of thread pools in total
        // might have some issues with threading if the same section is saved from multiple threads?
        this.savingService.enqueueSave(section);
    }

    //Executes an update to the world and automatically updates all the parent mip layers up to level 4 (e.g. where 1 chunk section is 1 block big)
    public void insertUpdate(VoxelizedSection section) {
        //The >>1 is cause the world sections size is 32x32x32 vs the 16x16x16 of the voxelized section
        for (int lvl = 0; lvl < this.maxMipLevels; lvl++) {
            var worldSection = this.acquire(lvl, section.x >> (lvl + 1), section.y >> (lvl + 1), section.z >> (lvl + 1));
            int msk = (1<<(lvl+1))-1;
            int bx = (section.x&msk)<<(4-lvl);
            int by = (section.y&msk)<<(4-lvl);
            int bz = (section.z&msk)<<(4-lvl);
            boolean didChange = false;
            for (int y = by; y < (16>>lvl)+by; y++) {
                for (int z = bz; z < (16>>lvl)+bz; z++) {
                    for (int x = bx; x < (16>>lvl)+bx; x++) {
                        long newId = section.get(lvl, x-bx, y-by, z-bz);
                        long oldId = worldSection.set(x, y, z, newId);
                        didChange |= newId != oldId;
                    }
                }
            }

            //Need to release the section after using it
            if (didChange) {
                //Mark the section as dirty (enqueuing saving and geometry rebuild) and move to parent mip level
                this.markDirty(worldSection);
                worldSection.release();
            } else {
                //If nothing changed just need to release, dont need to update parent mips
                worldSection.release();
                break;
            }
        }
    }

    public int[] getLoadedSectionCacheSizes() {
        return this.sectionTracker.getCacheCounts();
    }

    public void shutdown() {
        try {this.storage.flush();} catch (Exception e) {System.err.println(e);}
        //Shutdown in this order to preserve as much data as possible
        try {this.ingestService.shutdown();} catch (Exception e) {System.err.println(e);}
        try {this.savingService.shutdown();} catch (Exception e) {System.err.println(e);}
        try {this.storage.close();} catch (Exception e) {System.err.println(e);}
    }
}
