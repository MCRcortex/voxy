package me.cortex.voxy.common.storage.compressors;

import me.cortex.voxy.common.storage.StorageCompressor;
import me.cortex.voxy.common.storage.config.CompressorConfig;
import me.cortex.voxy.common.storage.config.ConfigBuildCtx;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.world.SaveLoadSystem;
import me.cortex.voxy.common.world.service.SectionSavingService;
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
    public MemoryBuffer decompress(MemoryBuffer saveData) {
        var decompressed = new MemoryBuffer(SaveLoadSystem.BIGGEST_SERIALIZED_SECTION_SIZE);
        //TODO: mark the size of the decompressed data to verify its length later
        long size = nZSTD_decompress(decompressed.address, decompressed.size, saveData.address, saveData.size);
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
