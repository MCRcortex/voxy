package me.cortex.voxy.client.core.rendering.hierarchical;

//System to determine what nodes to remove from the hericial tree while retaining the property that all
// leaf nodes should have meshes
//This system is critical to prevent the geometry buffer from growing to large or for too many nodes to fill up
// the node system
public class TreeTrimmer {
    //Used to interact with the outside world
    private final ITrimInterface trimInterface;

    public TreeTrimmer(ITrimInterface trimInterface) {
        this.trimInterface = trimInterface;
    }

    public void computeTrimPoints() {
        //Do a bfs to find ending points to trim needs to be based on some, last used, metric

        //First stratagy is to compute a bfs and or generate a list of nodes sorted by last use time
        // the thing is that if we cull a mesh, it cannot be a leaf node
        // if it is a leaf node its parent node must have a mesh loaded

    }
}
