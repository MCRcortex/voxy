package me.cortex.neovoxy.common.config.compressors;

import me.cortex.neovoxy.common.util.MemoryBuffer;

public interface StorageCompressor {
    MemoryBuffer compress(MemoryBuffer saveData);

    MemoryBuffer decompress(MemoryBuffer saveData);

    void close();
}
