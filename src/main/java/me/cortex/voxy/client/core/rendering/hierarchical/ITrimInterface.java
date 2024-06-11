package me.cortex.voxy.client.core.rendering.hierarchical;

public interface ITrimInterface {
    //Last recorded/known use time of a nodes mesh, returns -1 if node doesnt have a mesh
    int lastUsedTime(int node);

    //Returns an integer with the bottom 24 bits being the ptr top 8 bits being count or something
    int getChildren(int node);

    //Returns a size of the nodes mesh, -1 if the node doesnt have a mesh
    int getNodeSize(int node);
}
