package me.cortex.voxy.common.storage;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import me.cortex.voxy.common.storage.lmdb.LMDBStorageBackend;
import net.minecraft.util.math.random.RandomSeed;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;

//Compresses the section data
public class CompressionStorageAdaptor extends StorageBackend {
    private final StorageCompressor compressor;
    private final StorageBackend child;
    public CompressionStorageAdaptor(StorageCompressor compressor, StorageBackend child) {
        this.compressor = compressor;
        this.child = child;
    }

    @Override
    public ByteBuffer getSectionData(long key) {
        var data = this.child.getSectionData(key);
        if (data == null) {
            return null;
        }
        var decompressed = this.compressor.decompress(data);
        MemoryUtil.memFree(data);
        return decompressed;
    }

    @Override
    public void setSectionData(long key, ByteBuffer data) {
        var cdata = this.compressor.compress(data);
        this.child.setSectionData(key, cdata);
        MemoryUtil.memFree(cdata);
    }

    @Override
    public void deleteSectionData(long key) {
        this.child.deleteSectionData(key);
    }

    @Override
    public void putIdMapping(int id, ByteBuffer data) {
        this.child.putIdMapping(id, data);
    }

    @Override
    public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
        return this.child.getIdMappingsData();
    }

    @Override
    public void flush() {
        this.child.flush();
    }

    @Override
    public void close() {
        this.compressor.close();
        this.child.close();
    }
}
