package me.cortex.voxy.client.core.rendering.building;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;

import java.util.function.LongConsumer;

public class SectionPositionUpdateFilterer {
    private static final int SLICES = 1<<2;
    public interface IChildUpdate {void accept(WorldSection section);}

    private final LongOpenHashSet[] slices = new LongOpenHashSet[SLICES];
    {
        for (int i = 0; i < this.slices.length; i++) {
            this.slices[i] = new LongOpenHashSet();
        }
    }

    private LongConsumer renderForwardTo;
    private IChildUpdate childUpdateCallback;

    public void setCallbacks(LongConsumer forwardTo, IChildUpdate childUpdateCallback) {
        if (this.renderForwardTo != null) {
            throw new IllegalStateException();
        }
        this.renderForwardTo = forwardTo;
        this.childUpdateCallback = childUpdateCallback;
    }

    public boolean watch(int lvl, int x, int y, int z) {
        return this.watch(WorldEngine.getWorldSectionId(lvl, x, y, z));
    }

    public boolean watch(long position) {
        var set = this.slices[getSliceIndex(position)];
        boolean added;
        synchronized (set) {
            added = set.add(position);
        }
        if (added) {
            //If we added it, immediately invoke for an update
            this.renderForwardTo.accept(position);
        }
        return added;
    }

    public boolean unwatch(int lvl, int x, int y, int z) {
        return this.unwatch(WorldEngine.getWorldSectionId(lvl, x, y, z));
    }

    public boolean unwatch(long position) {
        var set = this.slices[getSliceIndex(position)];
        synchronized (set) {
            return set.remove(position);
        }
    }

    public void maybeForward(WorldSection section, int type) {
        final long position = section.key;
        var set = this.slices[getSliceIndex(position)];
        boolean contains;
        synchronized (set) {
            contains = set.contains(position);
        }
        if (contains) {
            if (type == 3) {//If its both, propagate to the render service
                this.renderForwardTo.accept(position);
            } else {
                if (type == 2) {//If its only a existance update
                    this.childUpdateCallback.accept(section);
                } else {//If its only a geometry update
                    this.renderForwardTo.accept(position);
                }
            }
        }
    }

    private static int getSliceIndex(long value) {
        value = (value ^ value >>> 30) * -4658895280553007687L;
        value = (value ^ value >>> 27) * -7723592293110705685L;
        return (int) ((value ^ value >>> 31)&(SLICES-1));
    }
}
