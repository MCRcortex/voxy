package me.cortex.neovoxy.common.config.storage;

import me.cortex.neovoxy.common.config.IMappingStorage;
import me.cortex.neovoxy.common.util.MemoryBuffer;

import java.util.ArrayList;
import java.util.List;

public abstract class StorageBackend implements IMappingStorage {

    //Implementation may use the scratch buffer as the return value, it MUST NOT free the scratch buffer
    public abstract MemoryBuffer getSectionData(long key, MemoryBuffer scratch);

    public abstract void setSectionData(long key, MemoryBuffer data);

    public abstract void deleteSectionData(long key);

    public abstract void flush();

    public abstract void close();

    public List<StorageBackend> getChildBackends() {
        return List.of();
    }

    public final List<StorageBackend> collectAllBackends() {
        List<StorageBackend> backends = new ArrayList<>();
        backends.add(this);
        for (var child : this.getChildBackends()) {
            backends.addAll(child.collectAllBackends());
        }
        return backends;
    }
}
