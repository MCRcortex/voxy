package me.cortex.neovoxy.common.config.section;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.neovoxy.common.Logger;
import me.cortex.neovoxy.common.config.ConfigBuildCtx;
import me.cortex.neovoxy.common.config.storage.StorageBackend;
import me.cortex.neovoxy.common.config.storage.StorageConfig;
import me.cortex.neovoxy.common.util.ThreadLocalMemoryBuffer;
import me.cortex.neovoxy.common.world.SaveLoadSystem;
import me.cortex.neovoxy.common.world.SaveLoadSystem3;
import me.cortex.neovoxy.common.world.WorldSection;
import me.cortex.neovoxy.common.world.other.Mapper;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.LongConsumer;

public class SectionSerializationStorage extends SectionStorage {
    private final StorageBackend backend;
    public SectionSerializationStorage(StorageBackend storageBackend) {
        this.backend = storageBackend;
    }

    private static final ThreadLocalMemoryBuffer MEMORY_CACHE = new ThreadLocalMemoryBuffer(SaveLoadSystem.BIGGEST_SERIALIZED_SECTION_SIZE + 1024);

    public int loadSection(WorldSection into) {
        var data = this.backend.getSectionData(into.key, MEMORY_CACHE.get().createUntrackedUnfreeableReference());
        if (data != null) {
            if (!SaveLoadSystem3.deserialize(into, data)) {
                this.backend.deleteSectionData(into.key);
                //TODO: regenerate the section from children
                Arrays.fill(into._unsafeGetRawDataArray(), Mapper.AIR);
                Logger.error("Section " + into.lvl + ", " + into.x + ", " + into.y + ", " + into.z + " was unable to load, removing");
                return -1;
            } else {
                return 0;
            }
        } else {
            //TODO: if we need to fetch an lod from a server, send the request here and block until the request is finished
            // the response should be put into the local db so that future data can just use that
            // the server can also send arbitrary updates to the client for arbitrary lods
            return 1;
        }
    }


    @Override
    public void saveSection(WorldSection section) {
        var saveData = SaveLoadSystem3.serialize(section);
        this.backend.setSectionData(section.key, saveData);
        saveData.free();
    }

    @Override
    public void putIdMapping(int id, ByteBuffer data) {
        this.backend.putIdMapping(id, data);
    }

    @Override
    public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
        return this.backend.getIdMappingsData();
    }

    @Override
    public void flush() {
        this.backend.flush();
    }

    @Override
    public void close() {
        this.backend.close();
    }

    @Override
    public void iterateStoredSectionPositions(LongConsumer consumer) {
        this.backend.iterateStoredSectionPositions(consumer);
    }

    public static class Config extends SectionStorageConfig {
        public StorageConfig storage;

        @Override
        public SectionStorage build(ConfigBuildCtx ctx) {
            return new SectionSerializationStorage(this.storage.build(ctx));
        }

        public static String getConfigTypeName() {
            return "Serializer";
        }
    }
}
