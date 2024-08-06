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
    public MemoryBuffer compress(MemoryBuffer saveData) {
        MemoryBuffer compressedData  = new MemoryBuffer((int)ZSTD_COMPRESSBOUND(saveData.size));
        long compressedSize = nZSTD_compress(compressedData.address, compressedData.size, saveData.address, saveData.size, this.level);
        return compressedData.subSize(compressedSize);
    }

    @Override
    public MemoryBuffer decompress(MemoryBuffer saveData) {
        var decompressed = new MemoryBuffer(SaveLoadSystem.BIGGEST_SERIALIZED_SECTION_SIZE);
        long size = nZSTD_decompress(decompressed.address, decompressed.size, saveData.address, saveData.size);
        return decompressed.subSize(size);
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
