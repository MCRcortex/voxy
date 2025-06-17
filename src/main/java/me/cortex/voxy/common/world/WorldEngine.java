package me.cortex.voxy.common.world;

import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.config.section.SectionStorage;
import me.cortex.voxy.common.util.TrackedObject;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.commonImpl.VoxyInstance;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class WorldEngine {
    public static final int MAX_LOD_LAYER = 4;

    public static final int UPDATE_TYPE_BLOCK_BIT = 1;
    public static final int UPDATE_TYPE_CHILD_EXISTENCE_BIT = 2;
    public static final int UPDATE_FLAGS = UPDATE_TYPE_BLOCK_BIT | UPDATE_TYPE_CHILD_EXISTENCE_BIT;

    public interface ISectionChangeCallback {void accept(WorldSection section, int updateFlags);}
    public interface ISectionSaveCallback {void save(WorldEngine engine, WorldSection section);}

    private final TrackedObject thisTracker = TrackedObject.createTrackedObject(this);

    public final SectionStorage storage;
    private final Mapper mapper;
    private final ActiveSectionTracker sectionTracker;
    private ISectionChangeCallback dirtyCallback;
    private ISectionSaveCallback saveCallback;
    volatile boolean isLive = true;

    public void setDirtyCallback(ISectionChangeCallback callback) {
        this.dirtyCallback = callback;
    }

    public void setSaveCallback(ISectionSaveCallback callback) {
        this.saveCallback = callback;
    }

    public Mapper getMapper() {return this.mapper;}
    public boolean isLive() {return this.isLive;}

    public final @Nullable VoxyInstance instanceIn;
    private final AtomicInteger refCount = new AtomicInteger();
    volatile long lastActiveTime = System.currentTimeMillis();//Time in millis the world was last "active" i.e. had a total ref count or active section count of != 0

    public WorldEngine(SectionStorage storage) {
        this(storage, null);
    }

    public WorldEngine(SectionStorage storage, @Nullable VoxyInstance instance) {
        this.instanceIn = instance;

        this.storage = storage;
        this.mapper = new Mapper(this.storage);
        //5 cache size bits means that the section tracker has 32 separate maps that it uses
        this.sectionTracker = new ActiveSectionTracker(6, storage::loadSection, 2048, this);
    }

    public WorldSection acquireIfExists(int lvl, int x, int y, int z) {
        if (!this.isLive) throw new IllegalStateException("World is not live");
        return this.sectionTracker.acquire(lvl, x, y, z, true);
    }

    public WorldSection acquire(int lvl, int x, int y, int z) {
        if (!this.isLive) throw new IllegalStateException("World is not live");
        return this.sectionTracker.acquire(lvl, x, y, z, false);
    }

    public WorldSection acquire(long pos) {
        if (!this.isLive) throw new IllegalStateException("World is not live");
        return this.sectionTracker.acquire(pos, false);
    }

    public WorldSection acquireIfExists(long pos) {
        if (!this.isLive) throw new IllegalStateException("World is not live");
        return this.sectionTracker.acquire(pos, true);
    }

    public static final int POS_FORMAT_VERSION = 1;

    //TODO: Fixme/optimize, cause as the lvl gets higher, the size of x,y,z gets smaller so i can dynamically compact the format
    // depending on the lvl, which should optimize colisions and whatnot
    public static long getWorldSectionId(int lvl, int x, int y, int z) {
        return ((long)lvl<<60)|((long)(y&0xFF)<<52)|((long)(z&((1<<24)-1))<<28)|((long)(x&((1<<24)-1))<<4);//NOTE: 4 bits spare for whatever
    }

    public static int getLevel(long id) {
        return (int) ((id>>60)&0xf);
    }
    public static int getX(long id) {
        return (int) ((id<<36)>>40);
    }

    public static int getY(long id) {
        return (int) ((id<<4)>>56);
    }

    public static int getZ(long id) {
        return (int) ((id<<12)>>40);
    }

    public static String pprintPos(long pos) {
        return getLevel(pos)+"@["+getX(pos)+", "+getY(pos)+", " + getZ(pos)+"]";
    }

    //Marks a section as dirty, enqueuing it for saving and or render data rebuilding
    public void markDirty(WorldSection section) {
        this.markDirty(section, UPDATE_FLAGS);
    }

    public void markDirty(WorldSection section, int changeState) {
        if (!this.isLive) throw new IllegalStateException("World is not live");
        if (section.tracker != this.sectionTracker) {
            throw new IllegalStateException("Section is not from here");
        }
        if (this.dirtyCallback != null) {
            this.dirtyCallback.accept(section, changeState);
        }
        if (this.saveCallback != null) {
            this.saveCallback.save(this, section);
        }
    }

    public void addDebugData(List<String> debug) {
        debug.add("ACC/SCC: " + this.sectionTracker.getLoadedCacheCount()+"/"+this.sectionTracker.getSecondaryCacheSize());//Active cache count, Secondary cache counts
    }

    public int getActiveSectionCount() {
        return this.sectionTracker.getLoadedCacheCount();
    }

    public void free() {
        if (!this.isLive) throw new IllegalStateException();
        this.isLive = false;
        VarHandle.fullFence();
        //Cannot free while there are loaded sections
        if (this.sectionTracker.getLoadedCacheCount() != 0) {
            throw new IllegalStateException();
        }

        this.thisTracker.free();
        try {this.mapper.close();} catch (Exception e) {Logger.error(e);}
        try {this.storage.flush();} catch (Exception e) {Logger.error(e);}
        //Shutdown in this order to preserve as much data as possible
        try {this.storage.close();} catch (Exception e) {Logger.error(e);}
    }

    private static final long TIMEOUT_MILLIS = 10_000;//10 second timeout (is to long? or to short??)
    public boolean isWorldUsed() {
        if (!this.isLive) throw new IllegalStateException();
        return this.refCount.get() != 0 || this.sectionTracker.getLoadedCacheCount() != 0;
    }

    public boolean isWorldIdle() {
        if (this.isWorldUsed()) {
            this.lastActiveTime = System.currentTimeMillis();//Force an update if is not active
            VarHandle.fullFence();
            return false;
        }
        return TIMEOUT_MILLIS<(System.currentTimeMillis()-this.lastActiveTime);
    }

    public void markActive() {
        if (!this.isLive) throw new IllegalStateException();
        this.lastActiveTime = System.currentTimeMillis();
    }

    public void acquireRef() {
        if (!this.isLive) throw new IllegalStateException();
        this.refCount.incrementAndGet();
        this.lastActiveTime = System.currentTimeMillis();
    }

    public void releaseRef() {
        if (!this.isLive) throw new IllegalStateException();
        if (this.refCount.decrementAndGet()<0) {
            throw new IllegalStateException("ref count less than 0");
        }
        //TODO: maybe dont need to tick the last active time?
        this.lastActiveTime = System.currentTimeMillis();
    }
}
