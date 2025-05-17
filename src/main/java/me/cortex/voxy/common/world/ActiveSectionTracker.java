package me.cortex.voxy.common.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.cortex.voxy.common.util.VolatileHolder;
import me.cortex.voxy.common.world.other.Mapper;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;

public class ActiveSectionTracker {

    //Deserialize into the supplied section, returns true on success, false on failure
    public interface SectionLoader {int load(WorldSection section);}

    //Loaded section world cache, TODO: get rid of VolatileHolder and use something more sane

    private final Long2ObjectOpenHashMap<VolatileHolder<WorldSection>>[] loadedSectionCache;
    private final StampedLock[] locks;
    private final SectionLoader loader;

    private final int lruSize;
    private final StampedLock lruLock = new StampedLock();
    private final Long2ObjectLinkedOpenHashMap<WorldSection> lruSecondaryCache;//TODO: THIS NEEDS TO BECOME A GLOBAL STATIC CACHE

    @Nullable
    public final WorldEngine engine;

    public ActiveSectionTracker(int numSlicesBits, SectionLoader loader, int cacheSize) {
        this(numSlicesBits, loader, cacheSize, null);
    }

    @SuppressWarnings("unchecked")
    public ActiveSectionTracker(int numSlicesBits, SectionLoader loader, int cacheSize, WorldEngine engine) {
        this.engine = engine;

        this.loader = loader;
        this.loadedSectionCache = new Long2ObjectOpenHashMap[1<<numSlicesBits];
        this.lruSecondaryCache = new Long2ObjectLinkedOpenHashMap<>(cacheSize);
        this.locks = new StampedLock[1<<numSlicesBits];
        this.lruSize = cacheSize;
        for (int i = 0; i < this.loadedSectionCache.length; i++) {
            this.loadedSectionCache[i] = new Long2ObjectOpenHashMap<>(1024);
            this.locks[i] = new StampedLock();
        }
    }

    public WorldSection acquire(int lvl, int x, int y, int z, boolean nullOnEmpty) {
        return this.acquire(WorldEngine.getWorldSectionId(lvl, x, y, z), nullOnEmpty);
    }

    public WorldSection acquire(long key, boolean nullOnEmpty) {
        int index = this.getCacheArrayIndex(key);
        var cache = this.loadedSectionCache[index];
        final var lock = this.locks[index];
        VolatileHolder<WorldSection> holder = null;
        boolean isLoader = false;
        WorldSection section = null;

        {
            long stamp = lock.readLock();
            holder = cache.get(key);
            if (holder != null) {//Return already loaded entry
                section = holder.obj;
                if (section != null) {
                    section.acquire();
                    lock.unlockRead(stamp);
                    return section;
                }
                lock.unlockRead(stamp);
            } else {//Try to create holder
                holder = new VolatileHolder<>();
                long ws = lock.tryConvertToWriteLock(stamp);
                if (ws == 0) {//Failed to convert, unlock read and get write
                    lock.unlockRead(stamp);
                    stamp = lock.writeLock();
                } else {
                    stamp = ws;
                }
                var eHolder = cache.putIfAbsent(key, holder);//We put if absent because on failure to convert to write, it leaves race condition
                lock.unlockWrite(stamp);
                if (eHolder == null) {//We are the loader
                    isLoader = true;
                } else {
                    holder = eHolder;
                }
            }
        }

        if (isLoader) {
            long stamp = this.lruLock.writeLock();
            section = this.lruSecondaryCache.remove(key);
            this.lruLock.unlockWrite(stamp);
        }

        //If this thread was the one to create the reference then its the thread to load the section
        if (isLoader) {
            int status = 0;
            if (section == null) {//Secondary cache miss
                section = new WorldSection(WorldEngine.getLevel(key),
                        WorldEngine.getX(key),
                        WorldEngine.getY(key),
                        WorldEngine.getZ(key),
                        this);

                status = this.loader.load(section);

                if (status < 0) {
                    //TODO: Instead if throwing an exception do something better, like attempting to regen
                    //throw new IllegalStateException("Unable to load section: ");
                    System.err.println("Unable to load section " + section.key + " setting to air");
                    status = 1;
                }

                //TODO: REWRITE THE section tracker _again_ to not be so shit and jank, and so that Arrays.fill is not 10% of the execution time
                if (status == 1) {
                    //We need to set the data to air as it is undefined state
                    Arrays.fill(section.data, 0);
                }
            } else {
                section.primeForReuse();
            }

            section.acquire();
            VarHandle.fullFence();//Do not reorder setting this object
            holder.obj = section;
            VarHandle.fullFence();
            if (nullOnEmpty && status == 1) {//If its air return null as stated, release the section aswell
                section.release();
                return null;
            }
            return section;
        } else {
            VarHandle.fullFence();
            while ((section = holder.obj) == null) {
                VarHandle.fullFence();
                Thread.onSpinWait();
                Thread.yield();
            }

            //lock.lock();
            {//Dont think need to lock here
                if (section.tryAcquire()) {
                    return section;
                }
            }
            //lock.unlock();

            //We failed everything, try get it again
            return this.acquire(key, nullOnEmpty);
        }
    }

    void tryUnload(WorldSection section) {
        int index = this.getCacheArrayIndex(section.key);
        final var cache = this.loadedSectionCache[index];
        WorldSection sec = null;
        final var lock = this.locks[index];
        long stamp = lock.writeLock();
        {
            if (section.trySetFreed()) {
                var cached = cache.remove(section.key);
                var obj = cached.obj;
                if (obj == null) {
                    throw new IllegalStateException("This should be impossible");
                }
                if (obj != section) {
                    throw new IllegalStateException("Removed section not the same as the referenced section in the cache: cached: " + obj + " got: " + section + " A: " + WorldSection.ATOMIC_STATE_HANDLE.get(obj) + " B: " +WorldSection.ATOMIC_STATE_HANDLE.get(section));
                }
                sec = section;
            }
        }

        WorldSection aa = null;
        if (sec != null) {
            long stamp2 = this.lruLock.writeLock();
            WorldSection a = this.lruSecondaryCache.put(section.key, section);
            if (a != null) {
                throw new IllegalStateException("duplicate sections in cache is impossible");
            }
            //If cache is bigger than its ment to be, remove the least recently used and free it
            if (this.lruSize < this.lruSecondaryCache.size()) {
                aa = this.lruSecondaryCache.removeFirst();
            }
            this.lruLock.unlockWrite(stamp2);

        }

        lock.unlockWrite(stamp);

        if (aa != null) {
            aa._releaseArray();
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

    public int getLoadedCacheCount() {
        int res = 0;
        for (var cache : this.loadedSectionCache) {
            res += cache.size();
        }
        return res;
    }

    public int getSecondaryCacheSize() {
        return this.lruSecondaryCache.size();
    }

    public static void main(String[] args) {
        var tracker = new ActiveSectionTracker(1, a->0, 1<<10);

        var section = tracker.acquire(0,0,0,0, false);
        section.acquire();
        var section2 = tracker.acquire(0,0,0,0, false);
        section.release();
        section.release();
        section = tracker.acquire(0,0,0,0, false);
        section.release();

    }
}
