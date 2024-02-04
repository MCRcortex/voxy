package me.cortex.voxy.common.storage;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.nio.ByteBuffer;

public abstract class StorageBackend {

    public abstract ByteBuffer getSectionData(long key);

    public abstract void setSectionData(long key, ByteBuffer data);

    public abstract void deleteSectionData(long key);

    public abstract void putIdMapping(int id, ByteBuffer data);

    public abstract Int2ObjectOpenHashMap<byte[]> getIdMappingsData();

    public abstract void flush();

    public abstract void close();
}
