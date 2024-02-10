package me.cortex.voxy.common.storage.other;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.storage.StorageBackend;
import me.cortex.voxy.common.storage.StorageCompressor;
import me.cortex.voxy.common.storage.config.CompressorConfig;
import me.cortex.voxy.common.storage.config.ConfigBuildCtx;
import me.cortex.voxy.common.storage.config.StorageConfig;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

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

    public static class Config extends StorageConfig {
        public CompressorConfig compressor;
        public StorageConfig backend;

        @Override
        public StorageBackend build(ConfigBuildCtx ctx) {
            return new CompressionStorageAdaptor(this.compressor.build(ctx), this.backend.build(ctx));
        }

        public static String getConfigTypeName() {
            return "CompressionAdaptor";
        }
    }
}
