package me.cortex.voxy.client.core.rendering;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;

import java.util.concurrent.locks.StampedLock;
import java.util.function.LongConsumer;

import static me.cortex.voxy.common.world.WorldEngine.UPDATE_TYPE_BLOCK_BIT;

public class SectionUpdateRouter implements ISectionWatcher {
    private static final int SLICES = 1<<4;
    public interface IChildUpdate {void accept(WorldSection section);}

    private final Long2ByteOpenHashMap[] slices = new Long2ByteOpenHashMap[SLICES];
    private final StampedLock[] locks = new StampedLock[SLICES];
    {
        for (int i = 0; i < this.slices.length; i++) {
            this.slices[i] = new Long2ByteOpenHashMap();
            this.locks[i] = new StampedLock();
        }
    }

    private LongConsumer initialRenderMeshGen;
    private LongConsumer renderMeshGen;
    private IChildUpdate childUpdateCallback;

    public void setCallbacks(LongConsumer initialRenderMeshGen, LongConsumer renderMeshGen, IChildUpdate childUpdateCallback) {
        if (this.renderMeshGen != null) {
            throw new IllegalStateException();
        }
        this.initialRenderMeshGen = initialRenderMeshGen;
        this.renderMeshGen = renderMeshGen;
        this.childUpdateCallback = childUpdateCallback;
    }

    public boolean watch(int lvl, int x, int y, int z, int types) {
        return this.watch(WorldEngine.getWorldSectionId(lvl, x, y, z), types);
    }

    public boolean watch(long position, int types) {
        int idx = getSliceIndex(position);
        var set = this.slices[idx];
        var lock = this.locks[idx];
        byte delta = 0;
        {
            long stamp = lock.readLock();
            byte current = set.getOrDefault(position, (byte) 0);
            delta = (byte) (current&types);
            current |= (byte) types;
            delta ^= (byte) (current&types);
            if (delta != 0) {//Was change
                long ws = lock.tryConvertToWriteLock(stamp);
                if (ws == 0) {
                    lock.unlockRead(stamp);
                    stamp = lock.writeLock();
                    //We need to recompute as we failed to acquire an immediate write lock
                    current = set.getOrDefault(position, (byte) 0);
                    delta = (byte) (current&types);
                    current |= (byte) types;
                    delta ^= (byte) (current&types);
                    if (delta != 0)
                        set.put(position, current);
                } else {
                    stamp = ws;
                    set.put(position, current);
                }
            }
            lock.unlock(stamp);
        }
        if ((delta&UPDATE_TYPE_BLOCK_BIT)!=0) {
            //If we added it, immediately invoke for an update
            this.initialRenderMeshGen.accept(position);
        }
        return delta!=0;
    }

    public boolean unwatch(int lvl, int x, int y, int z, int types) {
        return this.unwatch(WorldEngine.getWorldSectionId(lvl, x, y, z), types);
    }

    public boolean unwatch(long position, int types) {//Types is types to unwatch
        int idx = getSliceIndex(position);
        var set = this.slices[idx];
        var lock = this.locks[idx];

        long stamp = lock.readLock();

        byte current = set.getOrDefault(position, (byte)0);
        if (current == 0) {
            throw new IllegalStateException("Section pos not in map " + WorldEngine.pprintPos(position));
        }
        boolean removed = false;
        if ((current&types) != 0) {//Was change
            long ws = lock.tryConvertToWriteLock(stamp);
            if (ws == 0) {//failed to get write lock, need to unlock, get write, then redo
                lock.unlockRead(stamp);
                stamp = lock.writeLock();

                current = set.getOrDefault(position, (byte)0);
                if (current == 0) {
                    throw new IllegalStateException("Section pos not in map " + WorldEngine.pprintPos(position));
                }
            } else {
                stamp = ws;
            }

            if ((current&types) != 0) {
                current &= (byte) ~types;
                if (current == 0) {
                    set.remove(position);
                    removed = true;
                } else {
                    set.put(position, current);
                }
            }
        }
        lock.unlock(stamp);
        return removed;
    }

    public int get(long position) {
        int idx = getSliceIndex(position);
        var set = this.slices[idx];
        var lock = this.locks[idx];
        long stamp = lock.readLock();
        int ret = set.getOrDefault(position, (byte) 0);
        lock.unlockRead(stamp);
        return ret;
    }

    public void forwardEvent(WorldSection section, int type) {
        final long position = section.key;

        int idx = getSliceIndex(position);
        var set = this.slices[idx];
        var lock = this.locks[idx];

        long stamp = lock.readLock();
        byte types = set.getOrDefault(position, (byte) 0);
        lock.unlockRead(stamp);

        if (types!=0) {
            if ((type&WorldEngine.UPDATE_TYPE_CHILD_EXISTENCE_BIT)!=0) {
                this.childUpdateCallback.accept(section);
            }
            if ((type& UPDATE_TYPE_BLOCK_BIT)!=0) {
                this.renderMeshGen.accept(section.key);
            }
        }
    }

    private static int getSliceIndex(long value) {
        value = (value ^ value >>> 30) * -4658895280553007687L;
        value = (value ^ value >>> 27) * -7723592293110705685L;
        return (int) ((value ^ value >>> 31)&(SLICES-1));
    }
}
