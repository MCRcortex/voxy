package me.cortex.voxy.common.storage.other;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import me.cortex.voxy.common.storage.StorageBackend;
import me.cortex.voxy.common.storage.config.ConfigBuildCtx;
import me.cortex.voxy.common.storage.config.StorageConfig;
import net.minecraft.util.math.random.RandomSeed;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//Segments the section data into multiple dbs
public class FragmentedStorageBackendAdaptor extends StorageBackend {
    private final StorageBackend[] backends;

    public FragmentedStorageBackendAdaptor(StorageBackend... backends) {
        this.backends = backends;
        int len = backends.length;
        if ((len&(len-1)) != (len-1)) {
            throw new IllegalArgumentException("Backend count not a power of 2");
        }
    }

    private int getSegmentId(long key) {
        return (int) (RandomSeed.mixStafford13(RandomSeed.mixStafford13(key)^key)&(this.backends.length-1));
    }

    //TODO: reencode the key to be shifted one less OR
    // use like a mix64 to shuffle the key in getSegmentId so that
    // multiple layers of spliced storage backends can be stacked

    @Override
    public ByteBuffer getSectionData(long key) {
        return this.backends[this.getSegmentId(key)].getSectionData(key);
    }

    @Override
    public void setSectionData(long key, ByteBuffer data) {
        this.backends[this.getSegmentId(key)].setSectionData(key, data);
    }

    @Override
    public void deleteSectionData(long key) {
        this.backends[this.getSegmentId(key)].deleteSectionData(key);
    }

    @Override
    public void putIdMapping(int id, ByteBuffer data) {
        //Replicate the mappings over all the dbs to mean the chance of recovery in case of corruption is 30x
        for (var backend : this.backends) {
            backend.putIdMapping(id, data);
        }
    }

    private record EqualingArray(byte[] bytes) {
        @Override
        public boolean equals(Object obj) {
            return Arrays.equals(this.bytes, ((EqualingArray)obj).bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(this.bytes);
        }
    }

    @Override
    public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
        Object2IntOpenHashMap<Int2ObjectOpenHashMap<EqualingArray>> verification = new Object2IntOpenHashMap<>();
        Int2ObjectOpenHashMap<EqualingArray> any = null;
        for (var backend : this.backends) {
            var mappings = backend.getIdMappingsData();
            if (mappings.isEmpty()) {
                //TODO: log a warning and attempt to replicate the data the other fragments
                continue;
            }
            var repackaged = new Int2ObjectOpenHashMap<EqualingArray>(mappings.size());
            for (var entry : mappings.int2ObjectEntrySet()) {
                repackaged.put(entry.getIntKey(), new EqualingArray(entry.getValue()));
            }
            verification.addTo(repackaged, 1);
            any = repackaged;
        }
        if (any == null) {
            return new Int2ObjectOpenHashMap<>();
        }

        if (verification.size() != 1) {
            System.err.println("Error id mapping not matching across all fragments, attempting to recover");
            Object2IntMap.Entry<Int2ObjectOpenHashMap<EqualingArray>> maxEntry = null;
            for (var entry : verification.object2IntEntrySet()) {
                if (maxEntry == null) { maxEntry = entry; }
                else {
                    if (maxEntry.getIntValue() < entry.getIntValue()) {
                        maxEntry = entry;
                    }
                }
            }

            var mapping = maxEntry.getKey();

            var out = new Int2ObjectOpenHashMap<byte[]>(mapping.size());
            for (var entry : mapping.int2ObjectEntrySet()) {
                out.put(entry.getIntKey(), entry.getValue().bytes);
            }
            return out;
        } else {
            var out = new Int2ObjectOpenHashMap<byte[]>(any.size());
            for (var entry : any.int2ObjectEntrySet()) {
                out.put(entry.getIntKey(), entry.getValue().bytes);
            }
            return out;
        }
    }

    @Override
    public void flush() {
        for (var db : this.backends) {
            db.flush();
        }
    }

    @Override
    public void close() {
        for (var db : this.backends) {
            db.close();
        }
    }

    @Override
    public List<StorageBackend> getChildBackends() {
        return List.of(this.backends);
    }

    public static class Config extends StorageConfig {
        public List<StorageConfig> backends = new ArrayList<>();

        @Override
        public List<StorageConfig> getChildStorageConfigs() {
            return new ArrayList<>(this.backends);
        }

        @Override
        public StorageBackend build(ConfigBuildCtx ctx) {
            StorageBackend[] builtBackends = new StorageBackend[this.backends.size()];
            for (int i = 0; i < this.backends.size(); i++) {
                //TODO: put each backend in a different folder?
                builtBackends[i] = this.backends.get(i).build(ctx);
            }
            return new FragmentedStorageBackendAdaptor(builtBackends);
        }

        public static String getConfigTypeName() {
            return "FragmentationAdaptor";
        }
    }
}
