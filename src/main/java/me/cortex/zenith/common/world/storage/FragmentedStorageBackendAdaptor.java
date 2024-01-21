package me.cortex.zenith.common.world.storage;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.zenith.common.world.storage.lmdb.LMDBStorageBackend;
import net.minecraft.util.math.random.RandomSeed;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;

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
        this.backends[0].putIdMapping(id, data);
    }

    @Override
    public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
        return this.backends[0].getIdMappingsData();
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
