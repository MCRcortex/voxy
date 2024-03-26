package me.cortex.voxy.common.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.cortex.voxy.common.util.VolatileHolder;
import me.cortex.voxy.common.world.other.Mapper;

import java.util.Arrays;

public class ActiveSectionTracker {
    //Deserialize into the supplied section, returns true on success, false on failure
    public interface SectionLoader {int load(WorldSection section);}

    //Loaded section world cache, TODO: get rid of VolatileHolder and use something more sane

    private final Long2ObjectOpenHashMap<VolatileHolder<WorldSection>>[] loadedSectionCache;
    private final SectionLoader loader;
    //private final SectionDataCache dataCache;

    @SuppressWarnings("unchecked")
    public ActiveSectionTracker(int numSlicesBits, SectionLoader loader) {
        this.loader = loader;
        this.loadedSectionCache = new Long2ObjectOpenHashMap[1<<numSlicesBits];
        for (int i = 0; i < this.loadedSectionCache.length; i++) {
            this.loadedSectionCache[i] = new Long2ObjectOpenHashMap<>(1024);
        }
    }

    public WorldSection acquire(int lvl, int x, int y, int z, boolean nullOnEmpty) {
        long key = WorldEngine.getWorldSectionId(lvl, x, y, z);
        var cache = this.loadedSectionCache[this.getCacheArrayIndex(key)];
        VolatileHolder<WorldSection> holder = null;
        boolean isLoader = false;
        synchronized (cache) {
            holder = cache.get(key);
            if (holder == null) {
                holder = new VolatileHolder<>();
                cache.put(key, holder);
                isLoader = true;
            }
            var section = holder.obj;
            if (section != null) {
                section.acquire();
                return section;
            }
        }
        //If this thread was the one to create the reference then its the thread to load the section
        if (isLoader) {
            var section = new WorldSection(lvl, x, y, z, this);
            int status = -1;//this.dataCache.load(section);
            if (status == -1) {//Cache miss
                status = this.loader.load(section);
            }

            if (status < 0) {
                //TODO: Instead if throwing an exception do something better, like attempting to regen
                //throw new IllegalStateException("Unable to load section: ");
                System.err.println("Unable to load section " + section.key + " setting to air");
                status = 1;
            }
            if (status == 1) {
                //We need to set the data to air as it is undefined state
                Arrays.fill(section.data, Mapper.withLight(Mapper.AIR, 15));//Since lighting is inverted
            }
            section.acquire();
            holder.obj = section;
            if (nullOnEmpty && status == 1) {//If its air return null as stated, release the section aswell
                section.release();
                return null;
            }
            return section;
        } else {
            WorldSection section = null;
            while ((section = holder.obj) == null)
                Thread.onSpinWait();

            synchronized (cache) {
                if (section.tryAcquire()) {
                    return section;
                }
            }
            return this.acquire(lvl, x, y, z, nullOnEmpty);
        }
    }

    void tryUnload(WorldSection section) {
        var cache = this.loadedSectionCache[this.getCacheArrayIndex(section.key)];
        synchronized (cache) {
            if (section.trySetFreed()) {
                if (cache.remove(section.key).obj != section) {
                    throw new IllegalStateException("Removed section not the same as the referenced section in the cache");
                }
            }
        }
    }

    private int getCacheArrayIndex(long pos) {
        return (int) (mixStafford13(pos) & (this.loadedSectionCache.length-1));
    }

    public static long mixStafford13(long seed) {
        seed = (seed ^ seed >>> 30) * -4658895280553007687L;
        seed = (seed ^ seed >>> 27) * -7723592293110705685L;
        return seed ^ seed >>> 31;
    }

    public int[] getCacheCounts() {
        int[] res = new int[this.loadedSectionCache.length];
        for (int i = 0; i < this.loadedSectionCache.length; i++) {
            res[i] = this.loadedSectionCache[i].size();
        }
        return res;
    }


    public static void main(String[] args) {
        var tracker = new ActiveSectionTracker(1, a->0);

        var section = tracker.acquire(0,0,0,0, false);
        section.acquire();
        var section2 = tracker.acquire(0,0,0,0, false);
        section.release();
        section.release();
        section = tracker.acquire(0,0,0,0, false);
        section.release();

    }
}
