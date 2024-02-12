package me.cortex.voxy.common.storage.other;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.storage.StorageBackend;
import me.cortex.voxy.common.storage.StorageCompressor;
import me.cortex.voxy.common.storage.config.CompressorConfig;
import me.cortex.voxy.common.storage.config.ConfigBuildCtx;
import me.cortex.voxy.common.storage.config.StorageConfig;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.List;

public class DelegatingStorageAdaptor extends StorageBackend {
    protected final StorageBackend delegate;
    public DelegatingStorageAdaptor(StorageBackend delegate) {
        this.delegate = delegate;
    }

    @Override
    public ByteBuffer getSectionData(long key) {
        return this.delegate.getSectionData(key);
    }

    @Override
    public void setSectionData(long key, ByteBuffer data) {
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
