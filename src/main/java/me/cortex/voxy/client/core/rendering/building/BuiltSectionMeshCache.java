package me.cortex.voxy.client.core.rendering.building;

import java.util.concurrent.ConcurrentHashMap;

//TODO: Have a second level disk cache

//TODO: instead of storing duplicate render geometry between here and gpu memory
// when a section is unloaded from the gpu, put it into a download stream and recover the BuiltSection
// and put that into the cache, then remove the uploaded mesh from the cache
public class BuiltSectionMeshCache {
    private static final BuiltSection HOLDER = new BuiltSection(-1);
    private final ConcurrentHashMap<Long, BuiltSection> renderCache = new ConcurrentHashMap<>(1000,0.75f,10);

    public BuiltSection getMesh(long key) {
        BuiltSection[] res = new BuiltSection[1];
        this.renderCache.computeIfPresent(key, (a, value) -> {
            if (value == HOLDER) {
                return value;
            }
            res[0] = value.clone();
            return value;
        });
        return res[0];
    }

    //Returns true if the mesh was used, (this is so the parent method can free mesh object)
    public boolean putMesh(BuiltSection mesh) {
        var mesh2 = this.renderCache.computeIfPresent(mesh.position, (id, value) -> {
            if (value != HOLDER) {
                value.free();
            }
            return mesh;
        });
        return mesh2 == mesh;
    }

    public void clearMesh(long key) {
        this.renderCache.computeIfPresent(key, (a,val)->{
            if (val != HOLDER) {
                val.free();
            }
            return HOLDER;
        });
    }

    public void markCache(long key) {
        this.renderCache.putIfAbsent(key, HOLDER);
    }

    public void unmarkCache(long key) {
        var mesh = this.renderCache.remove(key);
        if (mesh != null && mesh != HOLDER) {
            mesh.free();
        }
    }


    public void free() {
        for (var mesh : this.renderCache.values()) {
            if (mesh != HOLDER) {
                mesh.free();
            }
        }
    }

    public int getCount() {
        return this.renderCache.size();
    }
}
