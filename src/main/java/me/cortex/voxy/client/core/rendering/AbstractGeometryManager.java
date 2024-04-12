package me.cortex.voxy.client.core.rendering;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;

import java.util.concurrent.ConcurrentLinkedDeque;

public abstract class AbstractGeometryManager {
    protected int sectionCount = 0;
    protected final int maxSections;
    protected final ConcurrentLinkedDeque<BuiltSection> buildResults = new ConcurrentLinkedDeque<>();

    protected AbstractGeometryManager(int maxSections) {
        this.maxSections = maxSections;
    }

    abstract IntArrayList uploadResults();

    int getMaxSections() {
        return this.maxSections;
    }

    public void enqueueResult(BuiltSection sectionGeometry) {
        this.buildResults.add(sectionGeometry);
    }

    public abstract float getGeometryBufferUsage();

    public int getSectionCount() {
        return this.sectionCount;
    }

    public abstract void free();

}
