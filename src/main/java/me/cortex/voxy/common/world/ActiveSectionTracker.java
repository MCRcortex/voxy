package me.cortex.voxy.common.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.world.other.Mapper;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;

public class ActiveSectionTracker {

    //Deserialize into the supplied section, returns true on success, false on failure
    public interface SectionLoader {int load(WorldSection section);}

    //Loaded section world cache, TODO: get rid of VolatileHolder and use something more sane
    private static final class VolatileHolder <T> {
        private static final VarHandle PRE_ACQUIRE_COUNT;
        private static final VarHandle POST_ACQUIRE_COUNT;
        static {
            try {
                PRE_ACQUIRE_COUNT = MethodHandles.lookup().findVarHandle(VolatileHolder.class, "preAcquireCount", int.class);
                POST_ACQUIRE_COUNT = MethodHandles.lookup().findVarHandle(VolatileHolder.class, "postAcquireCount", int.class);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        public volatile int preAcquireCount;
        public volatile int postAcquireCount;
        public volatile T obj;
    }

    private final AtomicInteger loadedSections = new AtomicInteger();
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
        //TODO: add optional verification check to ensure this (or other critical systems) arnt being called on the render or server thread
        if (this.engine != null) this.engine.lastActiveTime = System.currentTimeMillis();
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
            this.loadedSections.incrementAndGet();
            long stamp2 = lock.readLock();
            long stamp = this.lruLock.writeLock();
            section = this.lruSecondaryCache.remove(key);

            WorldSection removal = null;
            if (section == null && (!this.lruSecondaryCache.isEmpty()) && this.lruSize+100<this.lruSecondaryCache.size()+this.getLoadedCacheCount()) {//Add a self clamping lru case for when there are alot of loaded sections
                removal = this.lruSecondaryCache.removeFirst();
            }

            this.lruLock.unlockWrite(stamp);
            if (section != null) {
                section.primeForReuse();
                section.acquire(1);
            }
            lock.unlockRead(stamp2);

            if (removal != null) {
                removal._releaseArray();
            }
        } else {
            VolatileHolder.PRE_ACQUIRE_COUNT.getAndAdd(holder, 1);
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
                    Logger.error("Unable to load section " + section.key + " setting to air");
                    status = 1;
                }

                //TODO: REWRITE THE section tracker _again_ to not be so shit and jank, and so that Arrays.fill is not 10% of the execution time
                if (status == 1) {
                    //We need to set the data to air as it is undefined state
                    int sky = 15;
                    int block = 0;
                    Arrays.fill(section.data, Mapper.composeMappingId((byte) (sky|(block<<4)),0,0));
                }
                section.acquire(1);
            }
            int preAcquireCount = (int) VolatileHolder.PRE_ACQUIRE_COUNT.getAndSet(holder, 0);
            section.acquire(preAcquireCount);//pre acquire amount
            VolatileHolder.POST_ACQUIRE_COUNT.set(holder, preAcquireCount);

            //TODO: mark if the section was loaded null

            VarHandle.storeStoreFence();//Do not reorder setting this object
            holder.obj = section;
            VarHandle.releaseFence();
            if (nullOnEmpty && status == 1) {//If its air return null as stated, release the section aswell
                section.release();
                return null;
            }
            return section;
        } else {
            //TODO: mark the time the loading started in nanos, then here if it has been a while, spin lock, else jump back to the executing service and do work
            VarHandle.fullFence();
            while ((section = holder.obj) == null) {
                VarHandle.fullFence();
                Thread.onSpinWait();
                Thread.yield();
            }

            //Try to acquire a pre lock
            if (0<((int)VolatileHolder.POST_ACQUIRE_COUNT.getAndAdd(holder, -1))) {
                //We managed to acquire one of the pre locks, so just return the section
                return section;
            } else {
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
    }

    void tryUnload(WorldSection section, int hints) {
        if (this.engine != null) this.engine.lastActiveTime = System.currentTimeMillis();
        if (section.isDirty&&this.engine!=null) {
            if (section.tryAcquire()) {
                if (section.setNotDirty()) {//If the section is dirty we must enqueue for saving
                    this.engine.saveSection(section);//can block
                }
                section.release(false, hints);//Special
            }
        }

        if (section.getRefCount() != 0) {
            return;
        }
        int index = this.getCacheArrayIndex(section.key);
        final var cache = this.loadedSectionCache[index];
        WorldSection sec = null;
        final var lock = this.locks[index];
        long stamp = lock.writeLock();
        try {
            VarHandle.loadLoadFence();
            if (section.isDirty) {
                if (section.tryAcquire()) {
                    if (section.setNotDirty()) {//If the section is dirty we must enqueue for saving
                        if (this.engine != null)
                            this.engine.saveSection(section, true);//not allowed to block as we are in a lock
                    }
                    section.release(false, hints);//Special
                } else {
                    throw new IllegalStateException("Section was dirty but is also unloaded, this is very bad");
                }
            }
            if (section.getRefCount() == 0 && section.trySetFreed()) {
                var cached = cache.remove(section.key);
                var obj = cached.obj;
                if (obj == null) {
                    throw new IllegalStateException("This should be impossible: " + WorldEngine.pprintPos(section.key) + " secObj: " + System.identityHashCode(section));
                }
                if (obj != section) {
                    throw new IllegalStateException("Removed section not the same as the referenced section in the cache: cached: " + obj + " got: " + section + " A: " + WorldSection.ATOMIC_STATE_HANDLE.get(obj) + " B: " +WorldSection.ATOMIC_STATE_HANDLE.get(section));
                }
                sec = section;
            }
        } finally {
            if (sec == null) {
                lock.unlockWrite(stamp);
            }
        }

        WorldSection aa = null;
        if (sec != null) {
            long stamp2 = this.lruLock.writeLock();
            lock.unlockWrite(stamp);
            WorldSection a = this.lruSecondaryCache.put(section.key, section);
            if (a != null) {
                throw new IllegalStateException("duplicate sections in cache is impossible");
            }
            //If cache is bigger than its ment to be, remove the least recently used and free it
            if (this.lruSize < this.lruSecondaryCache.size()) {
                aa = this.lruSecondaryCache.removeFirst();
            }
            this.lruLock.unlockWrite(stamp2);

        } else {
            lock.unlockWrite(stamp);
        }


        if (aa != null) {
            aa._releaseArray();
        }

        if (sec != null) {
            this.loadedSections.decrementAndGet();
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
        return this.loadedSections.get();
    }

    public int getSecondaryCacheSize() {
        return this.lruSecondaryCache.size();
    }

    public static void main(String[] args) throws InterruptedException {
        var tracker = new ActiveSectionTracker(6, a->0, 2<<10);
        var bean = tracker.acquire(0, 0, 0, 9, false);
        var bean2 = tracker.acquire(1, 0, 0, 0, false);
        System.out.println("Target obj:" + System.identityHashCode(bean2));
        bean2.release();
        Thread[] ts = new Thread[10];
        for (int i = 0; i < ts.length;i++) {
            int tid = i;
            ts[i] = new Thread(()->{
                try {
                    for (int j = 0; j < 5000; j++) {
                        if (true) {
                            var section = tracker.acquire(0, 0, 0, 0, false);
                            section.acquire();
                            var section2 = tracker.acquire(1, 0, 0, 0, false);
                            section.release();
                            section.release();
                            section2.release();
                        }
                        if (true) {

                            var section = tracker.acquire(0, 0, 0, 0, false);
                            var section2 = tracker.acquire(1, 0, 0, 0, false);
                            section2.release();
                            section.release();
                        }
                        if (true) {
                            tracker.acquire(1, 0, 0, 0, false).release();
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Thread " + tid, e);
                }
            });
            ts[i].start();
        }
        for (var t : ts) {
            t.join();
        }
    }
}
