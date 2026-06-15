package me.cortex.neovoxy.common.config;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.nio.ByteBuffer;
import java.util.function.LongConsumer;

public interface IMappingStorage {
    void iterateStoredSectionPositions(LongConsumer consumer);
    void putIdMapping(int id, ByteBuffer data);
    Int2ObjectOpenHashMap<byte[]> getIdMappingsData();
    void flush();
    void close();
}
