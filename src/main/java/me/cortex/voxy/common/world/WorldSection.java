package me.cortex.voxy.common.world;


import me.cortex.voxy.commonImpl.VoxyCommon;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

//Represents a loaded world section at a specific detail level
// holds a 32x32x32 region of detail
public final class WorldSection {
    public static final int SECTION_VOLUME = 32*32*32;
    public static final boolean VERIFY_WORLD_SECTION_EXECUTION = VoxyCommon.isVerificationFlagOn("verifyWorldSectionExecution");


    static final VarHandle ATOMIC_STATE_HANDLE;
    private static final VarHandle NON_EMPTY_CHILD_HANDLE;
    private static final VarHandle NON_EMPTY_BLOCK_HANDLE;
    private static final VarHandle IN_SAVE_QUEUE_HANDLE;
    private static final VarHandle IS_DIRTY_HANDLE;

    static {
        try {
            ATOMIC_STATE_HANDLE = MethodHandles.lookup().findVarHandle(WorldSection.class, "atomicState", int.class);
            NON_EMPTY_CHILD_HANDLE = MethodHandles.lookup().findVarHandle(WorldSection.class, "nonEmptyChildren", byte.class);
            NON_EMPTY_BLOCK_HANDLE = MethodHandles.lookup().findVarHandle(WorldSection.class, "nonEmptyBlockCount", int.class);
            IN_SAVE_QUEUE_HANDLE = MethodHandles.lookup().findVarHandle(WorldSection.class, "inSaveQueue", boolean.class);
            IS_DIRTY_HANDLE = MethodHandles.lookup().findVarHandle(WorldSection.class, "isDirty", boolean.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }


    //TODO: should make it dynamically adjust the size allowance based on memory pressure/WorldSection allocation rate (e.g. is it doing a world import)
    private static final int ARRAY_REUSE_CACHE_SIZE = 400;//500;//32*32*32*8*ARRAY_REUSE_CACHE_SIZE == number of bytes
    //TODO: maybe just swap this to a ConcurrentLinkedDeque
    private static final AtomicInteger ARRAY_REUSE_CACHE_COUNT = new AtomicInteger(0);
    private static final ConcurrentLinkedDeque<long[]> ARRAY_REUSE_CACHE = new ConcurrentLinkedDeque<>();


    public final int lvl;
    public final int x;
    public final int y;
    public final int z;
    public final long key;


    //Serialized states
    long metadata;
    long[] data = null;
    volatile int nonEmptyBlockCount = 0;//Note: only needed for level 0 sections
    volatile byte nonEmptyChildren;

    final ActiveSectionTracker tracker;
    volatile boolean inSaveQueue;
    volatile boolean isDirty;

    //When the first bit is set it means its loaded
    @SuppressWarnings("all")
    private volatile int atomicState = 1;

    WorldSection(int lvl, int x, int y, int z, ActiveSectionTracker tracker) {
        this.lvl = lvl;
        this.x = x;
        this.y = y;
        this.z = z;
        this.key = WorldEngine.getWorldSectionId(lvl, x, y, z);
        this.tracker = tracker;

        this.data = ARRAY_REUSE_CACHE.poll();
        if (this.data == null) {
            this.data = new long[32 * 32 * 32];
        } else {
            ARRAY_REUSE_CACHE_COUNT.decrementAndGet();
        }
    }

    void primeForReuse() {
        ATOMIC_STATE_HANDLE.set(this, 1);
    }

    public long[] _unsafeGetRawDataArray() {
        return this.data;
    }

    @Override
    public int hashCode() {
        return ((x*1235641+y)*8127451+z)*918267913+lvl;
    }

    public boolean tryAcquire() {
        int prev, next;
        do {
            prev = (int) ATOMIC_STATE_HANDLE.get(this);
            if ((prev&1) == 0) {
                //The object has been release so early exit
                return false;
            }
            next = prev + 2;
        } while (!ATOMIC_STATE_HANDLE.compareAndSet(this, prev, next));
        return (next&1) != 0;


        /*
        int prev, next;
        do {
            prev = (int) ATOMIC_STATE_HANDLE.get(this);
            next = ((prev&1) != 0)?prev+2:prev;
        } while (!ATOMIC_STATE_HANDLE.compareAndSet(this, prev, next));
        return (next&1) != 0;
         */
    }

    public int acquire() {
        return this.acquire(1);
    }

    public int acquire(int count) {
        int state = ((int)  ATOMIC_STATE_HANDLE.getAndAdd(this, count<<1)) + (count<<1);
        if ((state & 1) == 0) {
            throw new IllegalStateException("Tried to acquire unloaded section: " + WorldEngine.pprintPos(this.key) + " obj: " + System.identityHashCode(this));
        }
        return state>>1;
    }

    public int getRefCount() {
        return ((int)ATOMIC_STATE_HANDLE.get(this))>>1;
    }

    //TODO: add the ability to hint to the tracker that yes the section is unloaded, try to cache it in a secondary cache since it will be reused/needed later
    public int release() {
        return release(true);
    }

    int release(boolean unload) {
        int state = ((int) ATOMIC_STATE_HANDLE.getAndAdd(this, -2)) - 2;
        if (state < 1) {
            throw new IllegalStateException("Section got into an invalid state");
        }
        if ((state & 1) == 0) {
            throw new IllegalStateException("Tried releasing a freed section");
        }
        if ((state>>1)==0 && unload) {
            if (this.tracker != null) {
                this.tracker.tryUnload(this);
            } else {
                //This should _ONLY_ ever happen when its an untracked section
                // If it is, try release it
                if (this.trySetFreed()) {
                    this._releaseArray();
                }
            }
        }
        return state>>1;
    }

    //Returns true on success, false on failure
    boolean trySetFreed() {
        int witness = (int) ATOMIC_STATE_HANDLE.compareAndExchange(this, 1, 0);
        if ((witness & 1) == 0 && witness != 0) {
            throw new IllegalStateException("Section marked as free but has refs");
        }
        if (witness == 1 && (this.isDirty || this.inSaveQueue)) {
            throw new IllegalStateException("Section freed while marked as dirty or in the save queue");
        }
        return witness == 1;
    }

    void _releaseArray() {
        if (VERIFY_WORLD_SECTION_EXECUTION && this.data == null) {
            throw new IllegalStateException();
        }
        if (ARRAY_REUSE_CACHE_COUNT.get() < ARRAY_REUSE_CACHE_SIZE) {
            ARRAY_REUSE_CACHE.add(this.data);
            ARRAY_REUSE_CACHE_COUNT.incrementAndGet();
        }
        this.data = null;
    }


    public void assertNotFree() {
        if (VERIFY_WORLD_SECTION_EXECUTION) {
            if ((((int) ATOMIC_STATE_HANDLE.get(this)) & 1) == 0) {
                throw new IllegalStateException();
            }
        }
    }

    public static int getIndex(int x, int y, int z) {
        final int M = (1<<5)-1;
        if (VERIFY_WORLD_SECTION_EXECUTION) {
            if (x < 0 || x > M || y < 0 || y > M || z < 0 || z > M) {
                throw new IllegalArgumentException("Out of bounds: " + x + ", " + y + ", " + z);
            }
        }
        return ((y&M)<<10)|((z&M)<<5)|(x&M);
    }

    public long set(int x, int y, int z, long id) {
        //TODO: this needs to update the block counts
        int idx = getIndex(x,y,z);
        long old = this.data[idx];
        this.data[idx] = id;
        return old;
    }

    //Generates a copy of the data array, this is to help with atomic operations like rendering
    public long[] copyData() {
        this.assertNotFree();
        return Arrays.copyOf(this.data, this.data.length);
    }

    public void copyDataTo(long[] cache) {
        copyDataTo(cache, 0);
    }

    public void copyDataTo(long[] cache, int dstOffset) {
        this.assertNotFree();
        if ((cache.length-dstOffset) < this.data.length) throw new IllegalArgumentException();
        System.arraycopy(this.data, 0, cache, dstOffset, this.data.length);
    }

    public static int getChildIndex(int x, int y, int z) {
        return (x&1)|((y&1)<<2)|((z&1)<<1);
    }

    public byte getNonEmptyChildren() {
        return (byte) NON_EMPTY_CHILD_HANDLE.get(this);
    }

    //Updates this.nonEmptyChildren atomically with respect to the child passed in
    // returns 0 if no change, 1 if it just updated and didnt do a major state change, 2 if it was a major state change (something -> nothing, nothing -> something)
    public int updateEmptyChildState(WorldSection child) {
        int childIdx = getChildIndex(child.x, child.y, child.z);
        byte msk = (byte) (1<<childIdx);
        byte prev, next;
        do {
            prev = this.getNonEmptyChildren();
            next = (byte) ((prev&(~msk))|(child.getNonEmptyChildren()!=0?msk:0));
        } while (!NON_EMPTY_CHILD_HANDLE.compareAndSet(this, prev, next));

        return ((prev!=0)^(next!=0))?2:(prev!=next?1:0);
    }

    public int getNonEmptyBlockCount() {
        return (int) NON_EMPTY_BLOCK_HANDLE.get(this);
    }

    public int addNonEmptyBlockCount(int delta) {
        int count = ((int)NON_EMPTY_BLOCK_HANDLE.getAndAdd(this, delta)) + delta;
        if (VERIFY_WORLD_SECTION_EXECUTION) {
            if (count < 0) {
                throw new IllegalStateException("Count is negative!");
            }
        }
        return count;
    }

    public boolean updateLvl0State() {
        if (VERIFY_WORLD_SECTION_EXECUTION) {
            if (this.lvl != 0) {
                throw new IllegalStateException("Tried updating a level 0 lod when its not level 0: " + WorldEngine.pprintPos(this.key));
            }
        }
        byte prev, next;
        do {
            prev = this.getNonEmptyChildren();
            next = (byte) (((int)NON_EMPTY_BLOCK_HANDLE.get(this))==0?0:0xFF);
        } while (!NON_EMPTY_CHILD_HANDLE.compareAndSet(this, prev, next));
        return prev != next;
    }

    public void _unsafeSetNonEmptyChildren(byte nonEmptyChildren) {
        NON_EMPTY_CHILD_HANDLE.set(this, nonEmptyChildren);
    }

    public static WorldSection _createRawUntrackedUnsafeSection(int lvl, int x, int y, int z) {
        return new WorldSection(lvl, x, y, z, null);
    }

    public boolean exchangeIsInSaveQueue(boolean state) {
        return ((boolean) IN_SAVE_QUEUE_HANDLE.compareAndExchange(this, !state, state)) == !state;
    }

    public void markDirty() {
        IS_DIRTY_HANDLE.getAndSet(this, true);
    }

    public boolean setNotDirty() {
        return (boolean) IS_DIRTY_HANDLE.getAndSet(this, false);
    }

    public boolean isFreed() {
        return (((int)ATOMIC_STATE_HANDLE.get(this))&1)==0;
    }
}