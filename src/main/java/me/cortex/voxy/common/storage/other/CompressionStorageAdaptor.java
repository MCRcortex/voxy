package me.cortex.voxy.common.storage.other;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.storage.StorageBackend;
import me.cortex.voxy.common.storage.StorageCompressor;
import me.cortex.voxy.common.storage.config.CompressorConfig;
import me.cortex.voxy.common.storage.config.ConfigBuildCtx;
import me.cortex.voxy.common.storage.config.StorageConfig;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.List;

//Compresses the section data
public class CompressionStorageAdaptor extends DelegatingStorageAdaptor {
    private final StorageCompressor compressor;
    public CompressionStorageAdaptor(StorageCompressor compressor, StorageBackend delegate) {
        super(delegate);
        this.compressor = compressor;
    }

    @Override
    public ByteBuffer getSectionData(long key) {
        var data = this.delegate.getSectionData(key);
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
        this.delegate.setSectionData(key, cdata);
        MemoryUtil.memFree(cdata);
    }

    @Override
    public void close() {
        this.compressor.close();
        super.close();
    }

    public static class Config extends DelegateStorageConfig {
        public CompressorConfig compressor;

        @Override
        public StorageBackend build(ConfigBuildCtx ctx) {
            return new CompressionStorageAdaptor(this.compressor.build(ctx), this.delegate.build(ctx));
        }

        public static String getConfigTypeName() {
            return "CompressionAdaptor";
        }
    }
}
