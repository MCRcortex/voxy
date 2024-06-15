package me.cortex.voxy.client.core.rendering.hierarchical;

import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.util.BufferArena;

//Manages the actual meshes, whether they are meshlets or whole meshes, the returned values are ids into a section
// array which contains metadata about the section
public class MeshManager {
    private final BufferArena geometryArena;
    private final GlBuffer sectionMetaBuffer;

    public MeshManager() {
        this.geometryArena = null;
        this.sectionMetaBuffer = null;
    }

    //Uploads the section geometry to the arena, there can be multiple meshes for the same geometry in the arena at the same time
    // it is not the MeshManagers responsiblity
    //The return value is arbitary as long as it can identify the mesh its uploaded until it is freed
    public int uploadMesh(BuiltSection section) {
        return uploadReplaceMesh(-1, section);
    }

    //Varient of uploadMesh that releases the previous mesh at the same time, this is a performance optimization
    public int uploadReplaceMesh(int old, BuiltSection section) {
        return -1;
    }

    public void removeMesh(int mesh) {

    }
}
