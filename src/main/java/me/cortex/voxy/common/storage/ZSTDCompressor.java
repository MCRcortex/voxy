package me.cortex.voxy.common.storage;

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
}
