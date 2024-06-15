package me.cortex.voxy.client.core.rendering.hierarchical;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.util.MarkedObjectList;
import me.cortex.voxy.common.world.WorldEngine;

import java.util.Arrays;


//TODO:FIXME: TODO, Must fix/have some filtering for section updates based on time or something
// as there can be a cursed situation where an update occures requiring expensive meshing for a section but then in the same
// tick it becomes air requiring no meshing thus basicly instantly emitting a result


//TODO: Make it an sparse voxel octree, that is if all of the children bar 1 are empty/air, remove the self node and replace it with the non empty child

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

        //Mesh ids for all the child nodes
        private final int[] meshIds = new int[8];
        {Arrays.fill(this.meshIds, -1);}

        //Positions for all the child nodes, should make SVO easier
        private final long[] childPositions = new long[8];
        {Arrays.fill(this.childPositions, -1);}



        //Reset/clear the request so that it may be reused
        public void clear() {
            this.position = 0;
            this.nodeId = 0;
            this.requiredChildMask = 0;
            this.currentChildMask  = 0;
            Arrays.fill(this.meshIds, -1);
            Arrays.fill(this.childPositions, -1);
        }

        //Returns true if the request is satisfied
        public boolean isSatisfied() {
            return (this.requiredChildMask&this.currentChildMask)==this.requiredChildMask;
        }

        public int getMeshId(int inner) {
            if (!this.isSet(inner)) {
                return -1;
            }
            return this.meshIds[inner];
        }

        public boolean isSet(int inner) {
            return (this.currentChildMask&(1<<inner))!=0;
        }

        public void put(int innerId, int mesh, long position) {
            this.currentChildMask |= (byte) (1<<innerId);
            this.meshIds[innerId] = mesh;
            this.childPositions[innerId] = position;
        }
    }

    public static final int MAX_NODE_COUNT = 1<<22;

    //Local data layout
    // first long is position (todo! might not be needed)
    // next long contains mesh position ig/id
    private final long[] localNodeData = new long[MAX_NODE_COUNT * 3];

    private final INodeInteractor interactor;
    private final MeshManager meshManager;

    public NodeManager2(INodeInteractor interactor, MeshManager meshManager) {
        this.interactor = interactor;
        this.pos2meshId.defaultReturnValue(NO_NODE);
        this.interactor.setMeshUpdateCallback(this::meshUpdate);
        this.meshManager = meshManager;
    }

    public void insertTopLevelNode(long position) {
        //NOTE! when initally adding a top level node, set it to air and request a meshing of the mesh
        // (if the mesh returns as air uhhh idk what to do cause a top level air node is kinda... not valid but eh)
        // that way the node will replace itself with its meshed varient when its ready aswell as prevent
        // the renderer from exploding, as it should ignore the empty sections entirly
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

    private static int pos2octnode(long pos) {
        return (WorldEngine.getX(pos)&1)|((WorldEngine.getY(pos)&1)<<1)|((WorldEngine.getZ(pos)&1)<<2);
    }

    //TODO: if all the children of a node become empty/removed traverse up the chain until a non empty parent node is hit and
    // remove all from the chain


    //TODO: test and fix the possible race condition of if a section is not empty then becomes empty in the same tick
    // that is, there is a request that is satisfied bar 1 section, that section is supplied as non emptpty but then becomes empty in the same tick
    private void meshUpdate(BuiltSection mesh) {
        int id = this.pos2meshId.get(mesh.position);
        if (id == NO_NODE) {
            //The built mesh section is no longer needed, discard it
            // TODO: could probably?? cache the mesh in ram that way if its requested? it can be immediatly fetched while a newer mesh is built??

            //This might be a warning? or maybe info?
            mesh.free();
            return;
        }

        if ((id&(1<<31))!=0) {
            //The mesh is part of a batched request
            id = id^(1<<31);//Basically abs it
            int innerId = pos2octnode(mesh.position);

            //There are a few cases for this branch
            // the section could be replacing an existing mesh that is part of the request (due to an update)
            // the section mesh could be new to the request
            //  in this case the section mesh could be the last entry needed to satisfy the request
            //      in which case! we must either A) mark the request as ready to be uploaded
            //      and then uploaded after all the mesh updates are processed, or upload it immediately

            LeafRequest request = this.leafRequests.get(id);

            //TODO: Get the mesh id if a mesh for the request at the same pos has already been submitted
            // then call meshManager.uploadReplaceMesh to get the new id, then put that into the request
            //TODO: could basicly make it a phase, where it then enqueues finished requests that then get uploaded later
            // that is dont immediatly submit request results, wait until the end of the frame
            // NOTE: COULD DO THIS WITH MESH RESULTS TOO, or could prefilter them per batch/tick
            int meshId;
            int prevMeshId = request.getMeshId(innerId);
            if (mesh.isEmpty()) {
                //since its empty, remove the previous mesh if it existed
                if (prevMeshId != -1) {
                    this.meshManager.removeMesh(prevMeshId);
                }
                meshId = -1;//FIXME: this is a hack to still result in the mesh being put in, but it is an empty mesh upload
            } else {
                if (prevMeshId != -1) {
                    meshId = this.meshManager.uploadReplaceMesh(prevMeshId, mesh);
                } else {
                    meshId = this.meshManager.uploadMesh(mesh);
                }
            }

            request.put(innerId, meshId, mesh.position);

            if (request.isSatisfied()) {
                //If request is now satisfied update the internal nodes, create the children and reset + release the request set
                this.completeRequest(request);

                //Reset + release
                request.clear();
                this.leafRequests.release(id);
            }
            //If the request is not yet satisfied, that is ok, continue ingesting new meshes until it is satisfied


        } else {
            //The mesh is an update for an existing node
            int prevMesh = this.getMeshForNode(id);
            // TODO: If the mesh to upload is air, the node should be removed (however i believe this is only true if all the children are air! fuuuuu)
            if (prevMesh != -1) {
                //Node has a mesh attached, remove and replace it
            } else {
                //Node didnt have a mesh attached, so just set the current mesh
            }
        }
    }


    private void completeRequest(LeafRequest request) {
        //TODO: need to actually update all of the pos2meshId of the children to point to there new nodes
    }

}
