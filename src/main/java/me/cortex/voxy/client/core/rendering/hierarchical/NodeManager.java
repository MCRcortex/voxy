package me.cortex.voxy.client.core.rendering.hierarchical;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.util.DownloadStream;
import me.cortex.voxy.common.util.HierarchicalBitSet;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL30.GL_R32UI;
import static org.lwjgl.opengl.GL30C.GL_RED_INTEGER;
import static org.lwjgl.opengl.GL45.nglClearNamedBufferSubData;

public class NodeManager {
    public static final int MAX_NODE_COUNT = 1<<22;
    public static final int MAX_REQUESTS = 1024;
    private final HierarchicalBitSet bitSet = new HierarchicalBitSet(MAX_NODE_COUNT);
    private final GlBuffer nodeBuffer = new GlBuffer(MAX_NODE_COUNT*16);//Node size is 16 bytes

    //TODO: maybe make this a coherent persistent mapped read buffer, instead of download synced buffer copy thing

    //a request payload is a single uint, first 8 bits are flags followed by 24 bit node identifier
    // (e.g. load child nodes, load child nodes + meshs, load self meshes )
    private final int REQUEST_QUEUE_SIZE = 4 + MAX_REQUESTS * 4;//TODO: add a priority system
    private final GlBuffer requestQueue = new GlBuffer(4 + MAX_REQUESTS * 4);

    //Buffer containing the index of the root nodes
    private final GlBuffer roots = new GlBuffer(1024*4);



    //500mb TODO: SEE IF CAN SHRINK IT BY EITHER NOT NEEDING AS MUCH SPACE or reducing max node count
    private final long[] localNodes = new long[MAX_NODE_COUNT * 3];//1.5x the size of the gpu copy to store extra metadata
    //LocalNodes have an up value pointing to the parent, enabling full traversal

    private final INodeInteractor interactor;

    public NodeManager(INodeInteractor interactor) {
        this.interactor = interactor;
        this.pos2meshId.defaultReturnValue(NO_NODE);
    }

    //Returns true if it has its own mesh loaded
    private static boolean nodeHasMeshLoaded(long metaA, long metaB) {
        return false;
    }

    private static final int REQUEST_SELF = 0;
    private static final int REQUEST_CHILDREN = 1;
    //A node can be loaded in the tree but have no mesh associated with it
    // this is so that higher level nodes dont waste mesh space


    //The reason that nodes have both child and own mesh pointers
    // is so that on an edge of the screen or when moving, nodes arnt constantly being swapped back and forth
    // it basicly acts as an inline cache :tm: however it does present some painpoints
    // especially in managing the graph

    //It might be easier to have nodes strictly either point to child nodes or meshes
    // if a parent needs to be rendered instead of the child, request for node change to self
    // while this will generate a shitton more requests it should be alot easier to manage graph wise
    // can probably add a caching service via a compute shader that ingests a request list
    // sees if the requested nodes are already cached, if so swap them in, otherwise dispatch a request
    // to cpu

    private void processRequestQueue(long ptr, long size) {
        int count = MemoryUtil.memGetInt(ptr); ptr += 4;
        for (int i = 0; i < count; i++) {
            int request = MemoryUtil.memGetInt(ptr + i*4L);
            int args = request&(0xFF<<24);
            int nodeId = request&(0xFFFFFF);

            long pos = this.localNodes[nodeId*3];
            long metaA = this.localNodes[nodeId*3 + 1];
            long metaB = this.localNodes[nodeId*3 + 2];

            int type = args&0b11;//2 bits for future request types such as parent and ensure stable (i.e. both parent and child loaded)
            if (type == REQUEST_SELF) {
                //Requires own mesh loaded (it can have 2 different priorites, it can fallback to using its children to render if they are loaded)
                // else it is critical priority
                if (nodeHasMeshLoaded(metaA, metaB)) {
                    throw new IllegalStateException("Node requested a mesh load, but mesh is already loaded: " + pos);
                }

                //watch the mesh and request it
                this.interactor.watchUpdates(pos);
                this.interactor.requestMesh(pos);

            } else if (type == REQUEST_CHILDREN) {
                //Node requires children to be loaded NOTE: when this is the case, it doesnt just mean the nodes,
                // it means the meshes aswell,
                // meshes may be unloaded later

                //when this case is hit it means that the child nodes arnt even loaded, so it becomes a bit more complex
                // basicly, need to request all child nodes be loaded in a batch
                // then in the upload tick need to do update many things

            } else {
                throw new IllegalArgumentException("Unknown update type: " + type + " @pos:" + pos);
            }

        }
    }


    public void uploadPhase() {
        //All uploads

        //Have a set of upload tasks for nodes,
        // this could include updating the mesh ptr
        // or child ptr or uploading new nodes
        // NOTE: when uploading a set of new nodes (must be clustered as children)
        // have to update parent
        // same when removing a set of children

        //Note: child node upload tasks need to all be complete before they can be uploaded


        //The way the graph works and can be cut is that all the leaf nodes _must_ at all times contain a mesh
        // this is critical to prevent "cracks"/no geometry being rendered
        // when the render mesh buffer is "full" (or even just periodicly), trimming of the tree must occur to keep
        // size within reason
        //Note tho that there is a feedback delay and such so geometry buffer should probably be trimmed when it reaches
        // 80-90% capacity so that new geometry can still be uploaded without being blocked on geometry clearing
        // it becomes a critical error if the geometry buffer becomes full while the tree is fully trimmed
        //NOTE: while trimming the tree, need to also trim the parents down i.e. the top level should really not have its mesh
        // loaded while it isnt really ever used
        // however as long as the rule that all leaf nodes have a mesh loaded is held then there should never be
        // any geometry holes
    }


    //Download and upload point, called once per frame
    public void downloadPhase() {
        DownloadStream.INSTANCE.download(this.requestQueue, 0, REQUEST_QUEUE_SIZE, this::processRequestQueue);
        DownloadStream.INSTANCE.commit();
        //Clear the queue counter, TODO: maybe do it some other way to batch clears
        nglClearNamedBufferSubData(this.requestQueue.id, GL_R32UI, 0, 4, GL_RED_INTEGER, GL_UNSIGNED_INT, 0);
        //TODO: compute cleanup here of loaded nodes, and what needs to be uploaded
        // i.e. if there is more upload stuff than there is free memory, cull nodes in the tree
        // to fit upload points, can also create errors if all nodes in the tree are requested but no memory to put
    }





    //Inserts a top level node into the graph, it has geometry and no children loaded as it is a leaf node
    public void insertTopLevelNode(long position) {

    }

    //Removes a top level node from the graph, doing so also removes all child nodes and associate geometry
    // the allocated slots when removing nodes are stored and roped off until it is guarenteed that all requests have
    // passed
    public void removeTopLevelNode(long position) {

    }



    //Tracking for nodes that specifically need meshes, if a node doesnt have or doesnt need a mesh node, it is not in the map
    // the map should be identical to the currently watched set of sections
    //NOTE: that if the id is negative its part of a mesh request
    private final Long2IntOpenHashMap pos2meshId = new Long2IntOpenHashMap();
    private static final int NO_NODE = -1;

    //Need to make this system attatched with a batched worker system, since a mesh update can be a few things
    // it can be a mesh update of a tracked render section, in this case we must ensure that it is still tracked and hasnt been removed bla bla bla
    //   if its still valid and tracked then upload it and update the node aswell ensuring sync bla bla bla
    // if it was part of a request, then we need to first check that the request still exists and hasnt been discarded  B) probably upload it immediatly still
    //   B) set the request with that section to have been, well, uploaded and the mesh set, (note if the mesh was updated while a request was inprogress/other requests not fufilled, need to remove the old and replace with the updated)
    //      if all the meshes in the request are satisfied, upload the request nodes and update its parent
    // NOTE! batch requests where this is needed are only strictly required when children are requested in order to guarentee that all
    //      propertiy of leaf nodes must have meshes remains
    //(TODO: see when sync with main thread should be, in the renderer or here since the updates are dispatched offthread)
    // Note that the geometry buffer should have idk 20% free? that way meshes can always be inserted (same for the node buffer ig) maybe 10%? idk need to experiement
    //  if the buffer goes over this threshold, the tree/graph culler must start culling last/least used nodes somehow
    //  it should be an error if the geometry or node buffer fills up but there are no nodes/meshes to cull/remove
    public void meshUpdate(BuiltSection mesh) {
        int id = this.pos2meshId.get(mesh.position);
        if (id == NO_NODE) {
            //The built mesh section is no longer needed, discard it
            // TODO: could probably?? cache the mesh in ram that way if its requested? it can be immediatly fetched while a newer mesh is built??
            mesh.free();
            return;
        }
        if ((id&(1<<31))!=0) {
            //The mesh is part of a batched request
            id = id^(1<<31);

        } else {
            //The mesh is an update for an existing node
            //this.localNodes[id*3]
        }
    }


    //A node has a position    (64 bit)
    // a ptr to its own mesh   (24 bit)
    // a ptr to children nodes (24 bit)
    // flags                   (16 bit)
    //                         Total of 128 bits (16 bytes)

    //First 2 flag bits are a requested dispatch type (0 meaning no request and the 3 remaining states for different request types)
    // this ensures that over multiple frames the same node is not requested

    //Bits exist for whether or not the children have meshes loaded or if the parents have meshes loaded
    // the idea is to keep +-1 lod meshes loaded into vram to enable seemless transitioning
    // the only critical state is that if a mesh wants to be rendered it should be able to be rendered

    //Basicly, there are multiple things, it depends on the screensize error
    // if a node is close to needing its children loaded but they arnt, then request it but with a lower priority
    // if a node must need its children then request at a high prioirty
    // if a node doesnt have a mesh but all its children do than dispatch a medium priority to have its own mesh loaded
    //      but then just use the child meshes for rendering

}
