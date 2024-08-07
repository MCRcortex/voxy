package me.cortex.voxy.client.core.rendering.hierachical2;


import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.building.SectionPositionUpdateFilterer;
import me.cortex.voxy.client.core.rendering.section.AbstractSectionGeometryManager;
import me.cortex.voxy.client.core.util.ExpandingObjectAllocationList;
import me.cortex.voxy.common.world.WorldEngine;
import me.jellysquid.mods.sodium.client.util.MathUtil;
import org.lwjgl.system.MemoryUtil;

//Contains no logic to interface with the gpu, nor does it contain any gpu buffers
public class HierarchicalNodeManager {
    public static final int NODE_MSK = ((1<<24)-1);
    public final int maxNodeCount;
    private final NodeStore nodeData;
    private final Long2IntOpenHashMap activeSectionMap = new Long2IntOpenHashMap();
    private final ExpandingObjectAllocationList<LeafExpansionRequest> leafRequests = new ExpandingObjectAllocationList<>(LeafExpansionRequest[]::new);
    private final AbstractSectionGeometryManager geometryManager;
    private final SectionPositionUpdateFilterer updateFilterer;
    public HierarchicalNodeManager(int maxNodeCount, AbstractSectionGeometryManager geometryManager, SectionPositionUpdateFilterer updateFilterer) {
        if (!MathUtil.isPowerOfTwo(maxNodeCount)) {
            throw new IllegalArgumentException("Max node count must be a power of 2");
        }
        if (maxNodeCount>(1<<24)) {
            throw new IllegalArgumentException("Max node count cannot exceed 2^24");
        }
        this.activeSectionMap.defaultReturnValue(-1);
        this.updateFilterer = updateFilterer;
        this.maxNodeCount = maxNodeCount;
        this.nodeData = new NodeStore(maxNodeCount);
        this.geometryManager = geometryManager;



        new Thread(()->{
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            for(int x = -50; x<=50;x++) {
                for (int z = -50; z <= 50; z++) {
                    for (int y = -3; y <= 3; y++) {
                        updateFilterer.watch(0,x,y,z);
                        updateFilterer.unwatch(0,x,y,z);
                    }
                }
            }
        }).start();
    }



    public void processRequestQueue(int count, long ptr) {
        for (int requestIndex = 0; requestIndex < count; requestIndex++) {
            int op = MemoryUtil.memGetInt(ptr + (requestIndex * 4L));
            this.processRequest(op);
        }
    }

    private void processRequest(int op) {
        int node = op&NODE_MSK;
        if (!this.nodeData.nodeExists(node)) {
            throw new IllegalStateException("Tried processing a node that doesnt exist: " + node);
        }
        if (this.nodeData.nodeRequestInFlight(node)) {
            throw new IllegalStateException("Tried processing a node that already has a request in flight: " + node + " pos: " + WorldEngine.pprintPos(this.nodeData.nodePosition(node)));
        }
        this.nodeData.markRequestInFlight(node);

        long pos = this.nodeData.nodePosition(node);

        //2 branches, either its a leaf node -> emit a leaf request
        // or the nodes geometry must be empty (i.e. culled from the graph/tree) so add to tracker and watch
        if (this.nodeData.isLeafNode(node)) {
            //TODO: the localNodeData should have a bitset of what children are definitely empty
            // use that to msk the request, HOWEVER there is a race condition e.g.
            // leaf node is requested and has only 1 child marked as non empty
            // however then an update occures and a different child now becomes non empty,
            // this will trigger a processBuildResult for parent
            // so need to ensure that when that happens, if the parent has an inflight leaf expansion request
            // for the leaf request to be updated to account for the new maybe child node
            //  NOTE: a section can have empty geometry but some of its children might not, so need to mark and
            //  submit a node at that level but with empty section, (specially marked) so that the traversal
            //  can recurse into those children as needed

            //Enqueue a leaf expansion request
            var request = new LeafExpansionRequest(pos);
            int requestId = this.leafRequests.put(request);

            for (int i = 0; i < 8; i++) {
                long childPos = makeChildPos(pos, i);
                //Insert all the children into the tracking map with the node id
                this.activeSectionMap.put(childPos, 0);
            }
        } else {
            //Verify that the node section is not in the section store. if it is then it is a state desynchonization
            // Note that a section can be "empty" but some of its children might not be
        }
    }

    public void processBuildResult(BuiltSection section) {
        if (!section.isEmpty()) {
            this.geometryManager.uploadSection(section);
        } else {
            section.free();
        }

        int nodeId = this.activeSectionMap.get(section.position);
        if (nodeId == -1) {
            //Not tracked or mapped to a node!!!
        } else {
            //Part of a request (top bit is set to 1)
            if ((nodeId&(1<<31))!=0) {

            } else {
                //Not part of a request, just a node update,
                // however could result in a reallocation if it needs to mark a child position as being possibly visible

            }
        }
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
}
