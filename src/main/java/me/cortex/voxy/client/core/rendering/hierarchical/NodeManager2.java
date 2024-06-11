package me.cortex.voxy.client.core.rendering.hierarchical;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.util.MarkedObjectList;

public class NodeManager2 {
    //A request for making a new child nodes 
    private static final class LeafRequest {
        //LoD position identifier
        public long position;

        //Node id of the node the leaf request is for, note! While there is a leaf request, the node should not be unloaded or removed
        public int nodeId;

        //The mask of what child nodes are required
        public byte requiredChildMask;

        //The mask of currently supplied child node data
        public byte currentChildMask;

        //Reset/clear the request so that it may be reused
        public void clear() {

        }
    }

    public static final int MAX_NODE_COUNT = 1<<22;

    //Local data layout
    // first long is position (todo! might not be needed)
    // next long contains mesh position ig/id
    private final long[] localNodeData = new long[MAX_NODE_COUNT * 3];

    private final INodeInteractor interactor;

    public NodeManager2(INodeInteractor interactor) {
        this.interactor = interactor;
        this.pos2meshId.defaultReturnValue(NO_NODE);
        this.interactor.setMeshUpdateCallback(this::meshUpdate);
    }

    public void insertTopLevelNode(long position) {

    }

    public void removeTopLevelNode(long position) {

    }

    //Returns the mesh offset/id for the given node or -1 if it doesnt exist
    private int getMeshForNode(int node) {
        return -1;
    }


    //Tracking for nodes that specifically need meshes, if a node doesnt have or doesnt need a mesh node, it is not in the map
    // the map should be identical to the currently watched set of sections
    //NOTE: that if the id is negative its part of a mesh request
    private final Long2IntOpenHashMap pos2meshId = new Long2IntOpenHashMap();
    private static final int NO_NODE = -1;

    //The request queue should be like some array that can reuse objects to prevent gc nightmare + like a bitset to find an avalible free slot
    // hashmap might work bar the gc overhead
    private final MarkedObjectList<LeafRequest> leafRequests = new MarkedObjectList<>(LeafRequest[]::new, LeafRequest::new);


    private void meshUpdate(BuiltSection mesh) {
        int id = this.pos2meshId.get(mesh.position);
        if (id == NO_NODE) {
            //The built mesh section is no longer needed, discard it
            // TODO: could probably?? cache the mesh in ram that way if its requested? it can be immediatly fetched while a newer mesh is built??
            mesh.free();
            return;
        }
        if ((id&(1<<31))!=0) {
            //The mesh is part of a batched request
            id = id^(1<<31);//Basically abs it

            //There are a few cases for this branch
            // the section could be replacing an existing mesh that is part of the request (due to an update)
            // the section mesh could be new to the request
            //  in this case the section mesh could be the last entry needed to satisfy the request
            //      in which case! we must either A) mark the request as ready to be uploaded
            //      and then uploaded after all the mesh updates are processed, or upload it immediately

            //The lower 3 bits of the id specify the quadrant (8 pos) of the node in the request
            LeafRequest request = this.leafRequests.get(id>>3);


        } else {
            //The mesh is an update for an existing node

            int prevMesh = this.getMeshForNode(id);
            if (prevMesh != -1) {
                //Node has a mesh attached, remove and replace it
            } else {
                //Node didnt have a mesh attached, so just set the current mesh
            }
        }
    }

}
