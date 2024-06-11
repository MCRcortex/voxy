package me.cortex.voxy.client.core.rendering.hierarchical;

import me.cortex.voxy.client.core.rendering.building.BuiltSection;

import java.util.function.Consumer;

//Interface for node manager to interact with the outside world
public interface INodeInteractor {
    void watchUpdates(long pos);//marks pos as watching for updates, i.e. any LoD updates will trigger a callback
    void unwatchUpdates(long pos);//Unmarks a position for updates

    void requestMesh(long pos);//Explicitly requests a mesh at a position, run the callback

    void setMeshUpdateCallback(Consumer<BuiltSection> mesh);
}
