package me.cortex.neovoxy.common.config.storage.other;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.neovoxy.common.config.storage.StorageBackend;
import me.cortex.neovoxy.common.util.MemoryBuffer;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.LongConsumer;

public class DelegatingStorageAdaptor extends StorageBackend {
    protected final StorageBackend delegate;
    public DelegatingStorageAdaptor(StorageBackend delegate) {
        this.delegate = delegate;
    }

    @Override
    public void iterateStoredSectionPositions(LongConsumer consumer) {this.delegate.iterateStoredSectionPositions(consumer);}

    @Override
    public MemoryBuffer getSectionData(long key, MemoryBuffer scratch) {
        return this.delegate.getSectionData(key, scratch);
    }

    @Override
    public void setSectionData(long key, MemoryBuffer data) {
        this.delegate.setSectionData(key, data);
    }

    @Override
    public void deleteSectionData(long key) {
        this.delegate.deleteSectionData(key);
    }

    @Override
    public void putIdMapping(int id, ByteBuffer data) {
        this.delegate.putIdMapping(id, data);
    }

    @Override
    public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
        return this.delegate.getIdMappingsData();
    }

    @Override
    public void flush() {
        this.delegate.flush();
    }

    @Override
    public void close() {
        this.delegate.close();
    }

    @Override
    public List<StorageBackend> getChildBackends() {
        return List.of(this.delegate);
    }
}
