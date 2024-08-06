package me.cortex.voxy.common.storage;

import me.cortex.voxy.common.util.MemoryBuffer;

import java.nio.ByteBuffer;

public interface StorageCompressor {
    MemoryBuffer compress(MemoryBuffer saveData);

    MemoryBuffer decompress(MemoryBuffer saveData);

    void close();
}
