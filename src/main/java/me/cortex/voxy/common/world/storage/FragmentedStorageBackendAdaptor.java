package me.cortex.voxy.common.world.storage;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import me.cortex.voxy.common.world.storage.lmdb.LMDBStorageBackend;
import net.minecraft.util.math.random.RandomSeed;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;

//Segments the section data into multiple dbs
public class FragmentedStorageBackendAdaptor extends StorageBackend {
    private final StorageBackend[] backends = new StorageBackend[32];

    public FragmentedStorageBackendAdaptor(File directory) {
        try {
            Files.createDirectories(directory.toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        for (int i = 0; i < this.backends.length; i++) {
            this.backends[i] = new LMDBStorageBackend(directory.toPath().resolve("storage-db-"+i+".db").toFile());//
        }
    }


    //public static long getWorldSectionId(int lvl, int x, int y, int z) {
    //        return ((long)lvl<<60)|((long)(y&0xFF)<<52)|((long)(z&((1<<24)-1))<<28)|((long)(x&((1<<24)-1))<<4);//NOTE: 4 bits spare for whatever
    //    }
    //private int getSegmentId(long key) {
    //    return (int) (((key>>4)&1)|((key>>27)&0b10)|((key>>50)&0b100));
    //}

    private int getSegmentId(long key) {
        return (int) (RandomSeed.mixStafford13(RandomSeed.mixStafford13(key)^key)&0x1F);
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
}
