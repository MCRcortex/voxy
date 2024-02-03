package me.cortex.voxy.client.core.rendering.building;

import java.util.concurrent.ConcurrentHashMap;

//TODO: instead of storing duplicate render geometry between here and gpu memory
// when a section is unloaded from the gpu, put it into a download stream and recover the BuiltSection
// and put that into the cache, then remove the uploaded mesh from the cache
public class BuiltSectionMeshCache {
    private final ConcurrentHashMap<Long, BuiltSection> renderCache = new ConcurrentHashMap<>(1000,0.75f,10);

    public BuiltSection getMesh(long key) {
        return null;
    }

    //Returns true if the mesh was used, (this is so the parent method can free mesh object)
    public boolean putMesh(BuiltSection mesh) {
        return false;
    }

    public void clearMesh(long key) {
    }


    public void free() {

    }
}
