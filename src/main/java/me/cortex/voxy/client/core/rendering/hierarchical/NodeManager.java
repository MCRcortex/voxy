package me.cortex.voxy.client.core.rendering.hierarchical;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.util.DownloadStream;
import me.cortex.voxy.client.core.util.MarkedCachedObjectList;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.common.util.HierarchicalBitSet;
import me.cortex.voxy.common.world.WorldEngine;
import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;

import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL30.GL_R32UI;
import static org.lwjgl.opengl.GL30C.GL_RED_INTEGER;
import static org.lwjgl.opengl.GL45.nglClearNamedBufferSubData;


//TODO:FIXME: TODO, Must fix/have some filtering for section updates based on time or something
// as there can be a cursed situation where an update occures requiring expensive meshing for a section but then in the same
// tick it becomes air requiring no meshing thus basicly instantly emitting a result


//TODO: Make it an sparse voxel octree, that is if all of the children bar 1 are empty/air, remove the self node and replace it with the non empty child

public class NodeManager {
    //A request for making a new child nodes 
    private static final class LeafRequest {
        //LoD position identifier
        public long position;

        //Node id of the node the leaf request is for, note! While there is a leaf request, the node should not be unloaded or removed
        public int nodeId;

        //The mask of what child nodes are required
        //public byte requiredChildMask;

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
            //this.requiredChildMask = 0;
            this.currentChildMask  = 0;
            Arrays.fill(this.meshIds, -1);
            Arrays.fill(this.childPositions, -1);
        }

        //Returns true if the request is satisfied
        public boolean isSatisfied() {
            return this.currentChildMask == -1;//(this.requiredChildMask&this.currentChildMask)==this.requiredChildMask;
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

        public void make(int id, long position) {
            this.nodeId = id;
            this.position = position;
        }

        public byte nonAirMask() {
            byte res = 0;
            for (int i = 0; i < 8; i++) {
                if (this.meshIds[i] != -1) {
                    res |= (byte) (1<<i);
                }
            }
            return res;
        }
    }

    public static final int MAX_NODE_COUNT = 1<<22;
    public static final int MAX_MESH_ID = 1<<24;

    public static final int REQUEST_QUEUE_SIZE = 1024;
    public static final int MESH_MSK = MAX_MESH_ID-1;
    public static final int EMPTY_MESH_ID = MESH_MSK-1;
    public static final int NODE_MSK = (1<<24)-1;//NOTE!! IS DIFFERENT FROM MAX_NODE_COUNT as the MAX_NODE_COUNT is for the buffers, the ACTUAL MAX is NODE_MSK+1

    //Local data layout
    // first long is position
    // next long contains mesh position ig/id
    private final long[] localNodeData = new long[MAX_NODE_COUNT * 3];

    private final HierarchicalBitSet nodeAllocations = new HierarchicalBitSet(MAX_NODE_COUNT);

    private final INodeInteractor interactor;
    private final MeshManager meshManager;

    public final GlBuffer nodeBuffer;
    public final GlBuffer requestQueue;

    public NodeManager(INodeInteractor interactor, MeshManager meshManager) {
        this.interactor = interactor;
        this.pos2meshId.defaultReturnValue(NO_NODE);
        this.interactor.setMeshUpdateCallback(this::meshUpdate);
        this.meshManager = meshManager;
        this.nodeBuffer = new GlBuffer(MAX_NODE_COUNT*16);
        this.requestQueue = new GlBuffer(REQUEST_QUEUE_SIZE*4+4);
        Arrays.fill(this.localNodeData, 0);
    }

    public final Long2IntOpenHashMap rootPos2Id = new Long2IntOpenHashMap();
    public void insertTopLevelNode(long position) {
        //NOTE! when initally adding a top level node, set it to air and request a meshing of the mesh
        // (if the mesh returns as air uhhh idk what to do cause a top level air node is kinda... not valid but eh)
        // that way the node will replace itself with its meshed varient when its ready aswell as prevent
        // the renderer from exploding, as it should ignore the empty sections entirly
        this.rootPosRequests.add(position);
        this.interactor.watchUpdates(position);
        this.interactor.requestMesh(position);

    }

    public void removeTopLevelNode(long position) {

    }




    //Returns the mesh offset/id for the given node or -1 if it doesnt exist
    private int getNodeMesh(int node) {
        return (int) (this.localNodeData[node*3+1]&MESH_MSK);
    }

    private int getNodeChildPtr(int node) {
        return (int) ((this.localNodeData[node*3+1]>>>32)&NODE_MSK);
    }

    private int getNodeChildCnt(int node) {
        return (int) ((this.localNodeData[node*3+1]>>>61)&7)+1;
    }

    private long getNodePos(int node) {
        return this.localNodeData[node*3];
    }

    private boolean isLeafNode(int node) {
        //TODO: maybe make this flag based instead of checking the child ptr?
        return this.getNodeChildPtr(node) == NODE_MSK;
    }

    //Its ment to return if the node is just an empty mesh or if all the children are also empty
    private boolean isEmptyNode(int node) {
        return this.getNodeMesh(node)==EMPTY_MESH_ID;//Special case/reserved
    }

    private void setNodePosition(int node, long position) {
        this.localNodeData[node*3] = position;
    }

    private void setMeshId(int node, int mesh) {
        if (mesh > MESH_MSK) {
            throw new IllegalArgumentException();
        }
        long val = this.localNodeData[node*3+1];
        val &= ~MESH_MSK;
        val |= mesh;
        this.localNodeData[node*3+1] = val;
    }

    private void setChildPtr(int node, int childPtr, int count) {
        if (childPtr > NODE_MSK || ((childPtr!=NODE_MSK)&&count < 1)) {
            throw new IllegalArgumentException();
        }
        long val = this.localNodeData[node*3+1];
        //Put the count
        val &= ~(0x7L<<61);
        val |= Integer.toUnsignedLong(Math.max(count-1,0))<<61;
        //Put the child ptr
        val &= ~(Integer.toUnsignedLong(NODE_MSK)<<32);
        val |= Integer.toUnsignedLong(childPtr) << 32;
        this.localNodeData[node*3+1] = val;
    }





    private static long makeChildPos(long basePos, int addin) {
        int lvl = WorldEngine.getLevel(basePos);
        if (lvl == 0) {
            throw new IllegalArgumentException("Cannot create a child lower than lod level 0");
        }
        return WorldEngine.getWorldSectionId(lvl-1,
                (WorldEngine.getX(basePos)<<1)|(addin&1),
                (WorldEngine.getY(basePos)<<1)|((addin>>1)&1),
                (WorldEngine.getZ(basePos)<<1)|((addin>>2)&1));
    }



    //The idea is, since a graph node can be in effectivly only 3 states, if inner node -> may or may not have mesh, and, if leaf node -> has mesh, no children
    // the request queue only needs to supply the node id, since if its an inner node, it must be requesting for a mesh, while if its a leaf node, it must be requesting for children
    private void processRequestQueue(long ptr, long size) {
        int count = MemoryUtil.memGetInt(ptr); ptr += 4;
        if (count > REQUEST_QUEUE_SIZE*1.5) {
            System.err.println("CORRUPTED PROCESS REQUEST, IGNORING (had count of: "+count+")");
            return;
        }


        for (int i = 0; i < count; i++) {
            int requestOp = MemoryUtil.memGetInt(ptr + i*4L);
            int node = requestOp&NODE_MSK;
            //System.out.println("Got request for node: " + node);
            if (WorldEngine.getLevel(this.getNodePos(node)) == 0) {
                System.err.println("Got a request for node at level 0: " + node + " pos: " + this.getNodePos(node));
                continue;
            }
            if (this.isLeafNode(node)) {
                //If its a leaf node and it has a request, it must need the children
                if (this.getNodeMesh(node) == -1) {
                    throw new IllegalStateException("Leaf node doesnt have mesh");
                }
                //Create a new request
                int idx = this.leafRequests.allocate();
                var request = this.leafRequests.get(idx);

                {
                    long nodePos = this.getNodePos(node);
                    request.make(node, nodePos);//Request all child nodes
                    int requestIdx = idx|(1<<31);//First bit is set to 1 to indicate a request index instead of a node index

                    //Loop over all child positions and insert them into the queue
                    for (int j = 0; j < 8; j++) {
                        long child = makeChildPos(nodePos, j);
                        int prev = this.pos2meshId.putIfAbsent(child, requestIdx);
                        if (prev != NO_NODE) {
                            throw new IllegalArgumentException("Node pos already in request map");
                        }
                        //Mark the position as watching and force request an update
                        this.interactor.watchUpdates(child);
                        this.interactor.requestMesh(child);
                    }
                }
                //NOTE: dont unmark the node yet, as the request hasnt been satisfied

            } else {
                //If its not a leaf node, it must be missing the inner mesh so request it
                if (this.getNodeMesh(node) != MESH_MSK) {
                    //Node already has a mesh, ignore it, but might be a sign that an error has occured
                    throw new IllegalStateException("Requested a mesh for node, however the node already has a mesh");

                    //TODO: need to unmark the node that requested it, either that or only clear node data when a mesh has been removed

                } else {
                    //Put it into the map + watch and request
                    long pos = this.getNodePos(node);
                    long prev = this.pos2meshId.putIfAbsent(pos, node);
                    if (prev != NO_NODE) {
                        throw new IllegalStateException("Pos already has a node id attached");
                    }
                    this.interactor.watchUpdates(pos);
                    this.interactor.requestMesh(pos);
                }
            }
        }
    }




    //Tracking for nodes that specifically need meshes, if a node doesnt have or doesnt need a mesh node, it is not in the map
    // the map should be identical to the currently watched set of sections
    //NOTE: that if the id is negative its part of a mesh request
    private final Long2IntOpenHashMap pos2meshId = new Long2IntOpenHashMap();
    private final LongOpenHashSet rootPosRequests = new LongOpenHashSet();//
    private static final int NO_NODE = -1;

    //The request queue should be like some array that can reuse objects to prevent gc nightmare + like a bitset to find an avalible free slot
    // hashmap might work bar the gc overhead
    private final MarkedCachedObjectList<LeafRequest> leafRequests = new MarkedCachedObjectList<>(LeafRequest[]::new, LeafRequest::new);

    private static int pos2octnode(long pos) {
        return (WorldEngine.getX(pos)&1)|((WorldEngine.getY(pos)&1)<<1)|((WorldEngine.getZ(pos)&1)<<2);
    }

    //TODO: if all the children of a node become empty/removed traverse up the chain until a non empty parent node is hit and
    // remove all from the chain






    //TODO: FIXME: CRITICAL: if a section is empty when created, it wont get allocated a slot, however, the section might
    // become unempty due to an update!!! THIS IS REALLY BAD. since it doesnt have an allocation

    //TODO: test and fix the possible race condition of if a section is not empty then becomes empty in the same tick
    // that is, there is a request that is satisfied bar 1 section, that section is supplied as non emptpty but then becomes empty in the same tick

    //TODO: Fixme: need to fix/make it so that the system can know if every child (to lod0) is empty or if its just the current section
    private void meshUpdate(BuiltSection mesh) {
        int id = this.pos2meshId.get(mesh.position);
        //TODO: FIXME!! if we get a node that has an update and is watched but no id for it, it could be an update state from
        // an empty node to non empty node, this means we need to invalidate all the childrens positions and move them!
        // then also update the parent pointer
        //TODO: Also need a way to remove sections, requires shuffling stuff around
        if (id == NO_NODE) {
            //If its a top level node insertion request, insert the node
            if (this.rootPosRequests.remove(mesh.position)) {
                if (!mesh.isEmpty()) {
                    int top = this.nodeAllocations.allocateNext();
                    this.rootPos2Id.put(mesh.position, top);
                    this.setNodePosition(top, mesh.position);
                    this.setChildPtr(top, NODE_MSK, 0);
                    this.setMeshId(top, this.meshManager.uploadMesh(mesh));
                    this.pushNode(top);
                }
            } else {
                //The built mesh section is no longer needed, discard it
                // TODO: could probably?? cache the mesh in ram that way if its requested? it can be immediatly fetched while a newer mesh is built??
                //This might be a warning? or maybe info?
                mesh.free();
            }
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
                this.completeLeafRequest(request);

                //Reset + release
                request.clear();
                this.leafRequests.release(id);
            }
            //If the request is not yet satisfied, that is ok, continue ingesting new meshes until it is satisfied


        } else {
            //The mesh is an update for an existing node

            //Sanity check
            if (this.getNodePos(id) != mesh.position) {
                throw new IllegalStateException("Node position not same as mesh position");
            }

            int prevMesh = this.getNodeMesh(id);
            // TODO: If the mesh to upload is air, the node should be removed (however i believe this is only true if all the children are air! fuuuuu)
            if (prevMesh != -1) {
                //Node has a mesh attached, remove and replace it
                this.setMeshId(id, this.meshManager.uploadReplaceMesh(prevMesh, mesh));
            } else {
                //Node didnt have a mesh attached, so just set the current mesh
                this.setMeshId(id, this.meshManager.uploadMesh(mesh));
            }
            //Push the updated node to the gpu
            this.pushNode(id);
        }
    }


    private void completeLeafRequest(LeafRequest request) {
        //TODO: FIXME: need to make it so that if a nodes mesh is empty but there are children that exist that arnt empty
        // then it needs to still add the node, but just with an empty mesh flag
        int msk = Byte.toUnsignedInt(request.nonAirMask());
        int baseIdx = this.nodeAllocations.allocateNextConsecutiveCounted(Integer.bitCount(msk));
        int cnt = 0;
        for (int i = 0; i < 8; i++) {
            if ((msk&(1<<i))!=0) {
                //It means the section actually exists, so add and upload it
                // aswell as add it to the mapping + push the node
                int id = baseIdx+(cnt++);
                long pos = request.childPositions[i];

                //Put it in the mapping
                this.pos2meshId.putIfAbsent(pos, id);
                this.setNodePosition(id, pos);
                this.setMeshId(id, request.getMeshId(i));
                this.setChildPtr(id, NODE_MSK, 0);

                this.pushNode(id);//request it to be uploaded
            } else {
                //The section was empty, so just remove/skip it, but remove it from the map
                this.pos2meshId.remove(request.childPositions[i]);
            }
        }
        if (cnt == 0) {
            //This means that every child node didnt have a mesh, this is not good
            //throw new IllegalStateException("Every child node empty for node at " + request.position);
            System.err.println("Every child node empty for node at " + request.position);
            //this.setChildPtr(request.nodeId, baseIdx, 0);
        } else {
            //Set the ptr
            this.setChildPtr(request.nodeId, baseIdx, cnt);
            this.pushNode(request.nodeId);
        }

    }

    private final IntArrayList nodeUpdates = new IntArrayList();

    //Invalidates the node and tells it to be pushed to the gpu next slot, NOTE: pushing a node, clears any gpu side flags
    private void pushNode(int node) {
        //TODO: update the local struct with the current frame id to prevent it from being put in the queue multiple times
        this.nodeUpdates.add(node);
    }

    private void writeNode(long dst, int id) {
        long pos = this.localNodeData[id*3];
        MemoryUtil.memPutInt(dst, (int) (pos>>32)); dst += 4;
        MemoryUtil.memPutInt(dst, (int) pos); dst += 4;

        int flags = 0;
        flags |= this.isEmptyNode(id)?2:0;
        flags |= Math.max(0, this.getNodeChildCnt(id)-1)<<2;

        int a = this.getNodeMesh(id)|((flags&0xFF)<<24);
        int b = this.getNodeChildPtr(id)|(((flags>>8)&0xFF)<<24);
        //System.out.println("Setting mesh " + this.getNodeMesh(id) + " for node " + id);
        MemoryUtil.memPutInt(dst, a); dst += 4;
        MemoryUtil.memPutInt(dst, b); dst += 4;
    }

    public void upload() {
        for (int i = 0; i < this.nodeUpdates.size(); i++) {
            int node = this.nodeUpdates.getInt(i);
            long ptr = UploadStream.INSTANCE.upload(this.nodeBuffer, node*16L, 16);
            this.writeNode(ptr, node);
        }
        if (!this.nodeUpdates.isEmpty()) {
            UploadStream.INSTANCE.commit();//Cause we actually uploaded something (do it after cause it allows batch comitting thing)
            this.nodeUpdates.clear();
        }
    }

    public void download() {
        //this.pushNode(0);
        //Download the request queue then clear the counter (first 4 bytes)
        DownloadStream.INSTANCE.download(this.requestQueue, this::processRequestQueue);
        DownloadStream.INSTANCE.commit();
        nglClearNamedBufferSubData(this.requestQueue.id, GL_R32UI, 0, 4, GL_RED_INTEGER, GL_UNSIGNED_INT, 0);
    }


    public void free() {
        this.requestQueue.free();
        this.nodeBuffer.free();
    }

}
