package me.cortex.voxy.common.storage.inmemory;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import me.cortex.voxy.common.storage.StorageBackend;
import me.cortex.voxy.common.storage.config.ConfigBuildCtx;
import me.cortex.voxy.common.storage.config.StorageConfig;
import me.cortex.voxy.common.util.MemoryBuffer;
import net.minecraft.util.math.random.RandomSeed;
import org.apache.commons.lang3.stream.Streams;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.function.LongConsumer;

public class MemoryStorageBackend extends StorageBackend {
    private final Long2ObjectMap<MemoryBuffer>[] maps;
    private final Int2ObjectMap<ByteBuffer> idMappings = new Int2ObjectOpenHashMap<>();

    public MemoryStorageBackend() {
        this(4);
    }

    private Long2ObjectMap<MemoryBuffer> getMap(long key) {
        return this.maps[(int) (RandomSeed.mixStafford13(RandomSeed.mixStafford13(key)^key)&(this.maps.length-1))];
    }

    @Override
    public void iterateStoredSectionPositions(LongConsumer consumer) {
        for (var map : this.maps) {
            synchronized (map) {
                map.keySet().forEach(consumer);
            }
        }
    }

    public MemoryStorageBackend(int slicesBitCount) {
        this.maps = new Long2ObjectMap[1<<slicesBitCount];
        for (int i = 0; i < this.maps.length; i++) {
            this.maps[i] = new Long2ObjectOpenHashMap<>();
        }
    }

    @Override
    public MemoryBuffer getSectionData(long key) {
        var map = this.getMap(key);
        synchronized (map) {
            var data = map.get(key);
            if (data != null) {
                return data.copy();
            } else {
                return null;
            }
        }
    }

    @Override
    public void setSectionData(long key, MemoryBuffer data) {
        var map = this.getMap(key);
        synchronized (map) {
            var cpy = data.copy();
            var old = map.put(key, cpy);
            if (old != null) {
                old.free();
            }
        }
    }

    @Override
    public void deleteSectionData(long key) {
        var map = this.getMap(key);
        synchronized (map) {
            var data = map.remove(key);
            if (data != null) {
                data.free();
            }
        }
    }

    @Override
    public void putIdMapping(int id, ByteBuffer data) {
        synchronized (this.idMappings) {
            var cpy = MemoryUtil.memAlloc(data.remaining());
            MemoryUtil.memCopy(data, cpy);
            var prev = this.idMappings.put(id, cpy);
            if (prev != null) {
                MemoryUtil.memFree(prev);
            }
        }
    }

    @Override
    public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
        Int2ObjectOpenHashMap<byte[]> out = new Int2ObjectOpenHashMap<>();
        synchronized (this.idMappings) {
            for (var entry : this.idMappings.int2ObjectEntrySet()) {
                var buf = new byte[entry.getValue().remaining()];
                entry.getValue().get(buf);
                entry.getValue().rewind();
                out.put(entry.getIntKey(), buf);
            }
            return out;
        }
    }

    @Override
    public void flush() {

    }

    @Override
    public void close() {
        Streams.of(this.maps).map(Long2ObjectMap::values).flatMap(ObjectCollection::stream).forEach(MemoryBuffer::free);
        this.idMappings.values().forEach(MemoryUtil::memFree);
    }

    public static class Config extends StorageConfig {
        @Override
        public StorageBackend build(ConfigBuildCtx ctx) {
            return new MemoryStorageBackend();
        }

        public static String getConfigTypeName() {
            return "Memory";
        }
    }
}
