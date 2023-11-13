package me.cortex.voxelmon.core.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.cortex.voxelmon.core.rendering.RenderTracker;
import me.cortex.voxelmon.core.voxelization.VoxelizedSection;
import me.cortex.voxelmon.core.world.other.Mapper;
import me.cortex.voxelmon.core.world.service.SectionSavingService;
import me.cortex.voxelmon.core.world.service.VoxelIngestService;
import me.cortex.voxelmon.core.world.storage.StorageBackend;

import java.io.File;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

//Use an LMDB backend to store the world, use a local inmemory cache for lod sections
// automatically manages and invalidates sections of the world as needed
public class WorldEngine {
    private static final int ACTIVE_CACHE_SIZE = 10;

    public final StorageBackend storage;
    private final Mapper mapper;
    public final VoxelIngestService ingestService = new VoxelIngestService(this);
    public final SectionSavingService savingService;
    private RenderTracker renderTracker;

    public void setRenderTracker(RenderTracker tracker) {
        this.renderTracker = tracker;
    }

    public Mapper getMapper() {return this.mapper;}

    private final int maxMipLevels;

    //Loaded section world cache
    private final Long2ObjectOpenHashMap<WorldSection>[] loadedSectionCache;
    //TODO: also segment this up into an array
    private final Long2ObjectOpenHashMap<AtomicReference<WorldSection>> sectionLoadingLocks = new Long2ObjectOpenHashMap<>();

    //What this is used for is to keep N sections acquired per layer, this stops sections from constantly being
    // loaded and unloaded when accessed close together
    private final ConcurrentLinkedDeque<WorldSection>[] activeSectionCache;


    public WorldEngine(File storagePath, int savingServiceWorkers, int maxMipLayers) {
        this.maxMipLevels = maxMipLayers;
        this.loadedSectionCache = new Long2ObjectOpenHashMap[maxMipLayers];
        this.activeSectionCache = new ConcurrentLinkedDeque[maxMipLayers];
        for (int i = 0; i < maxMipLayers; i++) {
            this.loadedSectionCache[i] = new Long2ObjectOpenHashMap<>(1<<(16-i));
            this.activeSectionCache[i] = new ConcurrentLinkedDeque<>();
        }
        this.storage = new StorageBackend(storagePath);
        this.mapper = new Mapper(this.storage);
        this.savingService = new SectionSavingService(this, savingServiceWorkers);
    }

    //TODO: Fixme/optimize, cause as the lvl gets higher, the size of x,y,z gets smaller so i can dynamically compact the format
    // depending on the lvl, which should optimize colisions and whatnot
    public static long getWorldSectionId(int lvl, int x, int y, int z) {
        return ((long)lvl<<60)|((long)(y&0xFF)<<52)|((long)(z&((1<<24)-1))<<28)|((long)(x&((1<<24)-1))<<4);//NOTE: 4 bits spare for whatever
    }

    public static int getLvl(long packed) {
        return (int) (packed>>>60);
    }
    public static int getX(long packed) {
        return (int) ((packed<<12)>>40);
    }
    public static int getY(long packed) {
        return (int) ((packed<<4)>>56);
    }
    public static int getZ(long packed) {
        return (int) ((packed<<4)>>40);
    }

    //Try to unload the section from the world atomically, this is called from the saving service, or any release call which results in the refcount being 0
    public void tryUnload(WorldSection section) {
        synchronized (this.loadedSectionCache[section.lvl]) {
            if (section.getRefCount() != 0) {
                return;
            }
            //TODO: make a thing where it checks if the section is dirty, if it is, enqueue it for a save first and return

            section.setFreed();
            var removedSection = this.loadedSectionCache[section.lvl].remove(section.getKey());
            if (removedSection != section) {
                throw new IllegalStateException("Removed section not the same as attempted to remove");
            }
            if (section.isAcquired()) {
                throw new IllegalStateException("Section that was just removed got reacquired");
            }
        }
    }

    //Internal helper method for getOrLoad to segment up code
    private WorldSection unsafeLoadSection(long key, int lvl, int x, int y, int z) {
        var data = this.storage.getSectionData(key);
        if (data == null) {
            return new WorldSection(lvl, x, y, z, this);
        } else {
            var ret =  SaveLoadSystem.deserialize(this, lvl, x, y, z, data);
            if (ret != null) {
                return ret;
            } else {
                this.storage.deleteSectionData(key);
                return new WorldSection(lvl, x, y, z, this);
            }
        }
    }

    //Gets a loaded section or loads the section from storage
    public WorldSection getOrLoadAcquire(int lvl, int x, int y, int z) {
        long key = getWorldSectionId(lvl, x, y, z);

        AtomicReference<WorldSection> lock = null;
        AtomicReference<WorldSection> gotLock = null;
        synchronized (this.loadedSectionCache[lvl]) {
            var result = this.loadedSectionCache[lvl].get(key);
            if (result != null) {
                result.acquire();
                return result;
            }
            lock = new AtomicReference<>(null);
            synchronized (this.sectionLoadingLocks) {
                var finalLock = lock;
                gotLock = this.sectionLoadingLocks.computeIfAbsent(key, a -> finalLock);
            }
        }

        //We acquired the lock so load it
        if (gotLock == lock) {
            WorldSection loadedSection = this.unsafeLoadSection(key, lvl, x, y, z);
            loadedSection.acquire();


            //Insert the loaded section and set the loading lock to the loaded value
            synchronized (this.loadedSectionCache[lvl]) {
                this.loadedSectionCache[lvl].put(key, loadedSection);
                synchronized (this.sectionLoadingLocks) {
                    this.sectionLoadingLocks.remove(key);
                    lock.set(loadedSection);
                }
            }

            //Add to the active acquired cache and remove the last item if the size is over the limit
            {
                loadedSection.acquire();
                this.activeSectionCache[lvl].add(loadedSection);
                if (this.activeSectionCache[lvl].size() > ACTIVE_CACHE_SIZE) {
                    var last = this.activeSectionCache[lvl].pop();
                    last.release();
                }
            }

            return loadedSection;
        } else {
            lock = gotLock;
            //Another thread got the lock so spin wait for the section to load
            while (lock.get() == null) {
                Thread.onSpinWait();
            }
            var section = lock.get();
            //Fixme: try find a better solution for this

            //The issue with this is that the section could be unloaded when we acquire it cause of so many threading pain
            // so lock the section cache, try acquire the section, if we fail we must load the section again
            synchronized (this.loadedSectionCache[lvl]) {
                if (section.tryAcquire()) {
                    //We acquired the section successfully, return it
                    return section;
                }
            }
            //We failed to acquire the section, we must reload it
            return this.getOrLoadAcquire(lvl, x, y, z);
        }
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
            var worldSection = this.getOrLoadAcquire(lvl, section.x>>(lvl+1), section.y>>(lvl+1), section.z>>(lvl+1));
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
        var res = new int[this.maxMipLevels];
        for (int i = 0; i < this.maxMipLevels; i++) {
            res[i] = this.loadedSectionCache[i].size();
        }
        return res;
    }

    public void shutdown() {
        try {this.storage.flush();} catch (Exception e) {System.err.println(e);}
        //Shutdown in this order to preserve as much data as possible
        try {this.ingestService.shutdown();} catch (Exception e) {System.err.println(e);}
        try {this.savingService.shutdown();} catch (Exception e) {System.err.println(e);}
        try {this.storage.close();} catch (Exception e) {System.err.println(e);}
    }
}
