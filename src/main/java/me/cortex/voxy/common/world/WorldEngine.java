package me.cortex.voxy.common.world;

import me.cortex.voxy.common.storage.StorageCompressor;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.common.world.service.SectionSavingService;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.common.storage.StorageBackend;
import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;
import java.util.function.Consumer;

//Use an LMDB backend to store the world, use a local inmemory cache for lod sections
// automatically manages and invalidates sections of the world as needed
public class WorldEngine {
    public final StorageBackend storage;
    private final Mapper mapper;
    private final ActiveSectionTracker sectionTracker;
    public final VoxelIngestService ingestService;
    public final SectionSavingService savingService;
    private Consumer<WorldSection> dirtyCallback;
    private final int maxMipLevels;


    public void setDirtyCallback(Consumer<WorldSection> tracker) {
        this.dirtyCallback = tracker;
    }

    public Mapper getMapper() {return this.mapper;}

    public WorldEngine(StorageBackend storageBackend, int ingestWorkers, int savingServiceWorkers, int maxMipLayers) {
        this.maxMipLevels = maxMipLayers;
        this.storage = storageBackend;
        this.mapper = new Mapper(this.storage);
        //4 cache size bits means that the section tracker has 16 separate maps that it uses
        this.sectionTracker = new ActiveSectionTracker(3, this::unsafeLoadSection);

        this.savingService = new SectionSavingService(this, savingServiceWorkers);
        this.ingestService  = new VoxelIngestService(this, ingestWorkers);
    }

    private int unsafeLoadSection(WorldSection into) {
        var data = this.storage.getSectionData(into.key);
        if (data != null) {
            try {
                if (!SaveLoadSystem.deserialize(into, data, true)) {
                    this.storage.deleteSectionData(into.key);
                    //TODO: regenerate the section from children
                    Arrays.fill(into.data, Mapper.AIR);
                    System.err.println("Section " + into.lvl + ", " + into.x + ", " + into.y + ", " + into.z + " was unable to load, removing");
                    return -1;
                } else {
                    return 0;
                }
            } finally {
                MemoryUtil.memFree(data);
            }
        } else {
            //TODO: if we need to fetch an lod from a server, send the request here and block until the request is finished
            // the response should be put into the local db so that future data can just use that
            // the server can also send arbitrary updates to the client for arbitrary lods
            return 1;
        }
    }

    public WorldSection acquireIfExists(int lvl, int x, int y, int z) {
        return this.sectionTracker.acquire(lvl, x, y, z, true);
    }

    public WorldSection acquire(int lvl, int x, int y, int z) {
        return this.sectionTracker.acquire(lvl, x, y, z, false);
    }

    //TODO: Fixme/optimize, cause as the lvl gets higher, the size of x,y,z gets smaller so i can dynamically compact the format
    // depending on the lvl, which should optimize colisions and whatnot
    public static long getWorldSectionId(int lvl, int x, int y, int z) {
        return ((long)lvl<<60)|((long)(y&0xFF)<<52)|((long)(z&((1<<24)-1))<<28)|((long)(x&((1<<24)-1))<<4);//NOTE: 4 bits spare for whatever
    }

    public static int getLevel(long id) {
        return (int) ((id>>60)&0xf);
    }

    //TODO: check these shifts are correct for all the gets
    public static int getX(long id) {
        return (int) ((id<<36)>>40);
    }

    public static int getY(long id) {
        return (int) ((id<<4)>>56);
    }

    public static int getZ(long id) {
        return (int) ((id<<12)>>40);
    }

    //Marks a section as dirty, enqueuing it for saving and or render data rebuilding
    public void markDirty(WorldSection section) {
        if (this.dirtyCallback != null) {
            this.dirtyCallback.accept(section);
        }
        //TODO: add an option for having synced saving, that is when call enqueueSave, that will instead, instantly
        // save to the db, this can be useful for just reducing the amount of thread pools in total
        // might have some issues with threading if the same section is saved from multiple threads?
        this.savingService.enqueueSave(section);
    }


    //TODO: move this to auxilery class  so that it can take into account larger than 4 mip levels
    //Executes an update to the world and automatically updates all the parent mip layers up to level 4 (e.g. where 1 chunk section is 1 block big)
    public void insertUpdate(VoxelizedSection section) {//TODO: add a bitset of levels to update and if it should force update
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
