package me.cortex.voxy.common.storage.other;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.config.AbstractConfig;
import me.cortex.voxy.common.storage.StorageBackend;
import me.cortex.voxy.common.config.ConfigBuildCtx;
import me.cortex.voxy.common.storage.config.StorageConfig;

import java.nio.ByteBuffer;
import java.util.List;

public class ReadonlyCachingAdaptor extends StorageBackend {
    private final StorageBackend cache;
    private final StorageBackend onMiss;

    public ReadonlyCachingAdaptor(StorageBackend cache, StorageBackend onMiss) {
        this.cache = cache;
        this.onMiss = onMiss;
    }

    @Override
    public ByteBuffer getSectionData(long key) {
        var result = this.cache.getSectionData(key);
        if (result != null) {
            return result;
        }
        result = this.onMiss.getSectionData(key);
        if (result != null) {
            this.cache.setSectionData(key, result);
        }
        return result;
    }

    @Override
    public void setSectionData(long key, ByteBuffer data) {
        this.cache.setSectionData(key, data);
    }

    @Override
    public void deleteSectionData(long key) {
        this.cache.deleteSectionData(key);
    }

    @Override
    public void putIdMapping(int id, ByteBuffer data) {
        this.cache.putIdMapping(id, data);
    }

    @Override
    public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
        return null;
    }

    @Override
    public void flush() {
        this.cache.close();
        this.onMiss.close();
    }

    @Override
    public void close() {
        this.cache.close();
        this.onMiss.close();
    }

    public static class Config extends StorageConfig {
        public AbstractConfig<StorageBackend> cache;
        public AbstractConfig<StorageBackend> onMiss;

        @Override
        public StorageBackend build(ConfigBuildCtx ctx) {
            return new ReadonlyCachingAdaptor(this.cache.build(ctx), this.onMiss.build(ctx));
        }

        public static String getConfigTypeName() {
            return "ReadonlyCachingAdaptor";
        }
    }
}
