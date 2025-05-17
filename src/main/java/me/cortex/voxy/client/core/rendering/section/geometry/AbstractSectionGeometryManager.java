package me.cortex.voxy.client.core.rendering.section.geometry;

import me.cortex.voxy.client.core.rendering.building.BuiltSection;

import java.util.function.Consumer;

//Does not care about the position of the sections, multiple sections that have the same position can be uploaded
// it is up to the traversal system to manage what sections exist in the geometry buffer
// the system is basicly "dumb" as in it just follows orders
public abstract class AbstractSectionGeometryManager implements IGeometryManager {
    public final int maxSections;
    public final long geometryCapacity;
    protected AbstractSectionGeometryManager(int maxSections, long geometryCapacity) {
        if ((maxSections&(maxSections-1))!=0) {//TODO: Maybe not do this, as it isnt a strict requirement
            throw new IllegalArgumentException("Max sections should be a power of 2");
        }
        this.maxSections = maxSections;
        this.geometryCapacity = geometryCapacity;
    }

    //Note, calling uploadSection or uploadReplaceSection will free the supplied BuiltSection
    public int uploadSection(BuiltSection section) {return this.uploadReplaceSection(-1, section);}
    public abstract int uploadReplaceSection(int oldId, BuiltSection section);
    public abstract void removeSection(int id);
    public void tick() {}

    public void free() {}

    public abstract void downloadAndRemove(int id, Consumer<BuiltSection> callback);

    public abstract long getUsedCapacity();
    public long getRemainingCapacity() {
        return this.geometryCapacity - this.getUsedCapacity();
    }
}
