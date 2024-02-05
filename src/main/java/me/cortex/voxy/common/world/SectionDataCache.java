package me.cortex.voxy.common.world;


import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;

public class SectionDataCache {
    private record MapPair(Reference2LongOpenHashMap<SoftReference<long[]>> ref2pos, Long2ObjectMap<SoftReference<long[]>> pos2ref) {
        public MapPair() {
            this(new Reference2LongOpenHashMap<>(), new Long2ObjectOpenHashMap<>());
        }
    }
    private final MapPair[] maps;
    private final ReferenceQueue<long[]> cleanupQueue;
    public SectionDataCache(int sliceBits) {
        this.cleanupQueue = new ReferenceQueue<>();
        this.maps = new MapPair[1<<sliceBits];
        for (int i = 0; i < this.maps.length; i++) {
            this.maps[i] = new MapPair();
        }
    }

    private MapPair getMap(long pos) {
        return this.maps[(int) (ActiveSectionTracker.mixStafford13(pos)&(this.maps.length-1))];
    }

    public int loadAndPut(WorldSection section) {
        var map = this.getMap(section.key);
        synchronized (map) {
            var entry = map.pos2ref.get(section.key);
            if (entry == null) {//No entry in cache so put it in cache
                map.pos2ref.put(section.key, new SoftReference<>(section.data));
                return -1;
            }
            //var data = entry.data.get();
            //if (data == null) {
            //    map.remove(section.key);
            //    return -1;
            //}
            //System.arraycopy(data, 0, section.data, 0, data.length);
            return 0;
        }
    }
}
