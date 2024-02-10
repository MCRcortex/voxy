package me.cortex.voxy.common.storage.compressors;

import me.cortex.voxy.common.storage.StorageCompressor;
import me.cortex.voxy.common.storage.config.CompressorConfig;
import me.cortex.voxy.common.storage.config.ConfigBuildCtx;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.util.zstd.Zstd.*;

public class ZSTDCompressor implements StorageCompressor {
    private final int level;

    public ZSTDCompressor(int level) {
        this.level = level;
    }

    @Override
    public ByteBuffer compress(ByteBuffer saveData) {
        ByteBuffer compressedData  = MemoryUtil.memAlloc((int)ZSTD_COMPRESSBOUND(saveData.remaining()));
        long compressedSize = ZSTD_compress(compressedData, saveData, this.level);
        compressedData.limit((int) compressedSize);
        compressedData.rewind();
        return compressedData;
    }

    @Override
    public ByteBuffer decompress(ByteBuffer saveData) {
        var decompressed = MemoryUtil.memAlloc(32*32*32*8*2);
        long size = ZSTD_decompress(decompressed, saveData);
        decompressed.limit((int) size);
        return decompressed;
    }

    @Override
    public void close() {

    }

    public static class Config extends CompressorConfig {
        public int compressionLevel;

        @Override
        public StorageCompressor build(ConfigBuildCtx ctx) {
            return new ZSTDCompressor(this.compressionLevel);
        }

        public static String getConfigTypeName() {
            return "ZSTD";
        }
    }
}
