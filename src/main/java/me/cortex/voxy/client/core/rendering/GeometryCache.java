package me.cortex.voxy.client.core.rendering;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;

//CPU side cache for section geometry, not thread safe
public class GeometryCache {
    private final ReentrantLock lock = new ReentrantLock();
    private long maxCombinedSize;
    private long currentSize;
    private final Long2ObjectLinkedOpenHashMap<BuiltSection> cache = new Long2ObjectLinkedOpenHashMap<>();
    public GeometryCache(long maxSize) {
        this.setMaxTotalSize(maxSize);
    }

    public void setMaxTotalSize(long size) {
        this.maxCombinedSize = size;
    }

    //Puts the section into the cache
    public void put(BuiltSection section) {
        this.lock.lock();
        var prev = this.cache.put(section.position, section);
        this.currentSize += section.geometryBuffer.size;
        if (prev != null) {
            this.currentSize -= prev.geometryBuffer.size;
        }
        while (this.maxCombinedSize <= this.currentSize) {
            var entry = this.cache.removeFirst();
            this.currentSize -= entry.geometryBuffer.size;
            entry.free();
        }
        this.lock.unlock();
        if (prev != null) {
            prev.free();
        }
    }

    public BuiltSection remove(long position) {
        this.lock.lock();
        var section = this.cache.remove(position);
        if (section != null) {
            this.currentSize -= section.geometryBuffer.size;
        }
        this.lock.unlock();
        return section;
    }

    public void clear(long position) {
        var sec = this.remove(position);
        if (sec != null) {
            sec.free();
        }
    }

    public void free() {
        this.lock.lock();
        this.cache.values().forEach(BuiltSection::free);
        this.cache.clear();
        this.lock.unlock();
    }
}
