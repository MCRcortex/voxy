package me.cortex.voxy.common.storage.other;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.storage.StorageBackend;
import me.cortex.voxy.common.storage.config.ConfigBuildCtx;
import me.cortex.voxy.common.storage.config.StorageConfig;

import java.nio.ByteBuffer;
import java.util.List;

public class ReadonlyCachingLayer extends StorageBackend {
    private final StorageBackend cache;
    private final StorageBackend onMiss;

    public ReadonlyCachingLayer(StorageBackend cache, StorageBackend onMiss) {
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
        //TODO: replicate this data onto the cache
        return this.onMiss.getIdMappingsData();
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
        public StorageConfig cache;
        public StorageConfig onMiss;

        @Override
        public List<StorageConfig> getChildStorageConfigs() {
            return List.of(this.cache, this.onMiss);
        }

        @Override
        public StorageBackend build(ConfigBuildCtx ctx) {
            return new ReadonlyCachingLayer(this.cache.build(ctx), this.onMiss.build(ctx));
        }

        public static String getConfigTypeName() {
            return "ReadonlyCachingLayer";
        }
    }
}
