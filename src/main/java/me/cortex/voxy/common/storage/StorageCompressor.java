package me.cortex.voxy.common.storage;

import java.nio.ByteBuffer;

public interface StorageCompressor {
    ByteBuffer compress(ByteBuffer saveData);

    ByteBuffer decompress(ByteBuffer saveData);

    void close();
}
