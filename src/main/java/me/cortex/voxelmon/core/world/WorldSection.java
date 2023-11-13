package me.cortex.voxelmon.core.world;


import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

//Represents a loaded world section at a specific detail level
// holds a 32x32x32 region of detail
public final class WorldSection {
    public final int lvl;
    public final int x;
    public final int y;
    public final int z;

    ////Maps from a local id to global meaning it should be much cheaper to store in memory probably
    //private final int[] dataMapping = null;
    //private final short[] data = new short[32*32*32];
    final long[] data = new long[32*32*32];
    boolean definitelyEmpty = true;

    private final WorldEngine world;

    public WorldSection(int lvl, int x, int y, int z, WorldEngine worldIn) {
        this.lvl = lvl;
        this.x = x;
        this.y = y;
        this.z = z;
        this.world = worldIn;
    }

    @Override
    public int hashCode() {
        return ((x*1235641+y)*8127451+z)*918267913+lvl;
    }

    public final AtomicBoolean inSaveQueue = new AtomicBoolean();
    private final AtomicInteger usageCounts = new AtomicInteger();

    public int acquire() {
        this.assertNotFree();
        return this.usageCounts.getAndAdd(1);
    }

    //TODO: Fixme i dont think this is fully thread safe/correct
    public boolean tryAcquire() {
        if (this.freed) {
            return false;
        }
        this.usageCounts.getAndAdd(1);
        if (this.freed) {
            return false;
        }
        return true;
    }

    public int release() {
        this.assertNotFree();
        int i = this.usageCounts.addAndGet(-1);
        if (i < 0) {
            throw new IllegalStateException();
        }


        //NOTE: cant actually check for not free as at this stage it technically could be unloaded, as soon
        //this.assertNotFree();


        //Try to unload the section if its empty
        if (i == 0) {
            this.world.tryUnload(this);
        }
        return i;
    }

    private volatile boolean freed = false;
    void setFreed() {
        this.assertNotFree();
        this.freed = true;
    }

    public void assertNotFree() {
        if (this.freed) {
            throw new IllegalStateException();
        }
    }

    public boolean isAcquired() {
        return this.usageCounts.get() != 0;
    }

    public int getRefCount() {
        return this.usageCounts.get();
    }

    public long getKey() {
        return WorldEngine.getWorldSectionId(this.lvl, this.x, this.y, this.z);
    }

    public static int getIndex(int x, int y, int z) {
        int M = (1<<5)-1;
        if (x<0||x>M||y<0||y>M||z<0||z>M) {
            throw new IllegalArgumentException("Out of bounds: " + x + ", " + y + ", " + z);
        }
        return ((y&M)<<10)|((z&M)<<5)|(x&M);
    }

    public long set(int x, int y, int z, long id) {
        int idx = getIndex(x,y,z);
        long old = this.data[idx];
        this.data[idx] = id;
        return old;
    }

    //Generates a copy of the data array, this is to help with atomic operations like rendering
    public long[] copyData() {
        return Arrays.copyOf(this.data, this.data.length);
    }

    public boolean definitelyEmpty() {
        return this.definitelyEmpty;
    }
}

//TODO: for serialization, make a huffman encoding tree on the integers since that should be very very efficent for compression
