package me.cortex.voxy.client.core.rendering.hierachical;

import me.cortex.voxy.common.util.HierarchicalBitSet;
import org.lwjgl.system.MemoryUtil;

public final class NodeStore {
    public static final int EMPTY_GEOMETRY_ID = -1;
    public static final int NODE_ID_MSK = ((1<<24)-1);
    public static final int REQUEST_ID_MSK = ((1<<16)-1);
    public static final int GEOMETRY_ID_MSK = (1<<24)-1;
    public static final int MAX_GEOMETRY_ID = (1<<24)-3;
    private static final int SENTINEL_NULL_GEOMETRY_ID = (1<<24)-1;
    private static final int SENTINEL_EMPTY_GEOMETRY_ID = (1<<24)-2;
    private static final int SENTINEL_NULL_NODE_ID = NODE_ID_MSK;
    private static final int LONGS_PER_NODE = 4;
    private static final int INCREMENT_SIZE = 1<<16;
    private final HierarchicalBitSet allocationSet;
    private long[] localNodeData;
    public NodeStore(int maxNodeCount) {
        if (maxNodeCount>=SENTINEL_NULL_NODE_ID) {
            throw new IllegalArgumentException("Max count too large");
        }
        //Initial count is 1024
        this.localNodeData = new long[INCREMENT_SIZE*LONGS_PER_NODE];
        this.allocationSet = new HierarchicalBitSet(maxNodeCount);
    }

    private static int id2idx(int idx) {
        return idx*LONGS_PER_NODE;
    }

    public int allocate() {
        int id = this.allocationSet.allocateNext();
        if (id < 0) {
            throw new IllegalStateException("Failed to allocate node slot!");
        }
        this.ensureSized(id);
        this.clear(id);
        return id;
    }

    public int allocate(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Count cannot be <= 0 was " + count);
        }
        int id = this.allocationSet.allocateNextConsecutiveCounted(count);
        if (id < 0) {
            throw new IllegalStateException("Failed to allocate " + count + " consecutive nodes!!");
        }
        this.ensureSized(id + count);
        for (int i = 0; i < count; i++) {
            this.clear(id + i);
        }
        return id;
    }

    //Ensures that index is within the array, if not, resizes to contain it + buffer zone
    private void ensureSized(int index) {
        if (index*LONGS_PER_NODE >= this.localNodeData.length) {
            int newSize = Math.min((index+INCREMENT_SIZE), this.allocationSet.getLimit());

            long[] newStore = new long[newSize * LONGS_PER_NODE];
            System.arraycopy(this.localNodeData, 0, newStore, 0, this.localNodeData.length);
            this.localNodeData = newStore;
        }
    }

    public void free(int nodeId) {
        this.free(nodeId, 1);
    }

    public void free(int baseNodeId, int count) {
        for (int i = 0; i < count; i++) {
            int nodeId = baseNodeId + i;
            if (!this.allocationSet.free(nodeId)) {//TODO: add batch free
                throw new IllegalStateException("Node " + nodeId + " was not allocated!");
            }
            this.clear(nodeId);
        }
    }



    private void clear(int nodeId) {
        int idx = id2idx(nodeId);
        this.localNodeData[idx] = -1;//Position
        this.localNodeData[idx+1] = GEOMETRY_ID_MSK|(((long)NODE_ID_MSK)<<24);
        this.localNodeData[idx+2] = REQUEST_ID_MSK;
        this.localNodeData[idx+3] = 0;
    }


    //Copy from allocated index A to allocated index B
    public void copyNode(int fromId, int toId) {
        if (!(this.allocationSet.isSet(fromId)&&this.allocationSet.isSet(toId))) {
            throw new IllegalArgumentException();
        }
        int f = id2idx(fromId);
        int t = id2idx(toId);
        this.localNodeData[t  ] = this.localNodeData[f  ];
        this.localNodeData[t+1] = this.localNodeData[f+1];
        this.localNodeData[t+2] = this.localNodeData[f+2];
        this.localNodeData[t+3] = this.localNodeData[f+3];
    }

    public void setNodePosition(int node, long position) {
        this.localNodeData[id2idx(node)] = position;
    }

    public long nodePosition(int nodeId) {
        return this.localNodeData[id2idx(nodeId)];
    }

    public boolean nodeExists(int nodeId) {
        return this.allocationSet.isSet(nodeId);
    }

    public int getNodeGeometry(int node) {
        long data = this.localNodeData[id2idx(node)+1];
        int geometryPtr = (int) (data&GEOMETRY_ID_MSK);
        if (geometryPtr == SENTINEL_NULL_GEOMETRY_ID) {
            return -1;
        }
        if (geometryPtr == SENTINEL_EMPTY_GEOMETRY_ID) {
            return -2;
        }
        return geometryPtr;
    }

    public void setNodeGeometry(int node, int geometryId) {

        if (geometryId>MAX_GEOMETRY_ID) {
            throw new IllegalArgumentException("Geometry ptr greater than MAX_GEOMETRY_ID: " + geometryId);
        }

        if (geometryId == -1) {
            geometryId = SENTINEL_NULL_GEOMETRY_ID;
        }

        if (geometryId == -2) {
            geometryId = SENTINEL_EMPTY_GEOMETRY_ID;
        }

        if (geometryId < 0) {
            throw new IllegalArgumentException("Geometry ptr less than -1 : " + geometryId);
        }

        int idx = id2idx(node)+1;
        long data = this.localNodeData[idx];
        data &= ~GEOMETRY_ID_MSK;
        data |= geometryId;
        this.localNodeData[idx] = data;
    }

    public int getChildPtr(int nodeId) {
        long data = this.localNodeData[id2idx(nodeId)+1];
        int nodePtr = (int) ((data>>24)&NODE_ID_MSK);
        if (nodePtr == SENTINEL_NULL_NODE_ID) {
            return -1;
        }
        return nodePtr;
    }

    public void setChildPtr(int nodeId, int ptr) {
        if (ptr>=NODE_ID_MSK || ptr<-1) {
            throw new IllegalArgumentException("Node child ptr greater GEQ NODE_ID_MSK or less than -1 : " + ptr);
        }
        if (ptr == -1) {
            ptr = SENTINEL_NULL_NODE_ID;
        }

        int idx = id2idx(nodeId)+1;
        long data = this.localNodeData[idx];
        data &= ~(((long)NODE_ID_MSK)<<24);
        data |= ((long)ptr)<<24;
        this.localNodeData[idx] = data;
    }

    public void setNodeRequest(int node, int requestId) {
        int id = id2idx(node)+2;
        long data = this.localNodeData[id];
        data &= ~REQUEST_ID_MSK;
        data |= requestId;
        this.localNodeData[id] = data;
    }

    public int getNodeRequest(int node) {
        return (int) (this.localNodeData[id2idx(node)+2]&REQUEST_ID_MSK);
    }

    public void markRequestInFlight(int nodeId) {
        this.localNodeData[id2idx(nodeId)+1] |= 1L<<63;
    }
    public void unmarkRequestInFlight(int nodeId) {
        this.localNodeData[id2idx(nodeId)+1] &= ~(1L<<63);
    }
    public boolean isNodeRequestInFlight(int nodeId) {
        return ((this.localNodeData[id2idx(nodeId)+1]>>63)&1)!=0;
    }

    //TODO: Implement this in node manager
    public void setAllChildrenAreLeaf(int nodeId, boolean state) {
        this.localNodeData[id2idx(nodeId)+2] &= ~(1L<<16);
        this.localNodeData[id2idx(nodeId)+2] |= state?1L<<16:0;
    }

    public boolean getAllChildrenAreLeaf(int nodeId) {
        return ((this.localNodeData[id2idx(nodeId)+2]>>16)&1)!=0;
    }

    public void markNodeGeometryInFlight(int nodeId) {
        this.localNodeData[id2idx(nodeId)+1] |= 1L<<59;
    }
    public void unmarkNodeGeometryInFlight(int nodeId) {
        this.localNodeData[id2idx(nodeId)+1] &= ~(1L<<59);
    }
    public boolean isNodeGeometryInFlight(int nodeId) {
        return (this.localNodeData[id2idx(nodeId)+1]&(1L<<59))!=0;
    }

    public int getNodeType(int nodeId) {
        return (int)((this.localNodeData[id2idx(nodeId)+1]>>61)&3)<<30;
    }

    public void setNodeType(int nodeId, int type) {
        type >>>= 30;
        int idx = id2idx(nodeId)+1;
        long data = this.localNodeData[idx];
        data &= ~(3L<<61);
        data |= ((long)type)<<61;
        this.localNodeData[idx] = data;
    }

    public byte getNodeChildExistence(int nodeId) {
        long data = this.localNodeData[id2idx(nodeId)+1];
        return (byte) ((data>>48)&0xFF);
    }

    public void setNodeChildExistence(int nodeId, byte existence) {
        int idx = id2idx(nodeId)+1;
        long data = this.localNodeData[idx];
        data &= ~(0xFFL<<48);
        data |= Byte.toUnsignedLong(existence)<<48;
        this.localNodeData[idx] = data;
    }

    public int getChildPtrCount(int nodeId) {
        long data = this.localNodeData[id2idx(nodeId)+1];
        return ((int)((data>>56)&0x7))+1;
    }

    public void setChildPtrCount(int nodeId, int count) {
        if (count <= 0 || count>8) throw new IllegalArgumentException("Count: " + count);
        int idx = id2idx(nodeId)+1;
        long data = this.localNodeData[idx];
        data &= ~(7L<<56);
        data |= ((long) (count - 1)) <<56;
        this.localNodeData[idx] = data;
    }

    //Writes out a nodes data to the ptr in the compacted/reduced format
    public void writeNode(long ptr, int nodeId) {
        if (!this.nodeExists(nodeId)) {
            MemoryUtil.memPutLong(ptr, -1);
            MemoryUtil.memPutLong(ptr + 8, -1);
            return;
        }
        long pos = this.nodePosition(nodeId);
        MemoryUtil.memPutInt(ptr, (int) (pos>>32)); ptr += 4;
        MemoryUtil.memPutInt(ptr, (int) pos); ptr += 4;

        int z = 0;
        int w = 0;

        short flags = 0;
        flags |= (short) (this.isNodeRequestInFlight(nodeId)?1:0);//1 bit
        flags |= (short) ((this.getChildPtrCount(nodeId)-1)<<2);//3 bit

        boolean isEligibleForCleaning = false;
        isEligibleForCleaning |= this.getAllChildrenAreLeaf(nodeId);
        //isEligibleForCleaning |= this.getNodeType()

        flags |= (short) (isEligibleForCleaning?1<<5:0);//1 bit

        {
            int geometry = this.getNodeGeometry(nodeId);
            if (geometry == -2) {
                z |= 0xFFFFFF-1;//This is a special case, which basically says to the renderer that the geometry is empty (not that it doesnt exist)
            } else if (geometry == -1) {
                z |= 0xFFFFFF;//Special case null
            } else {
                z |= geometry&0xFFFFFF;//TODO: check and ensure bounds
            }
        }
        int childPtr = this.getChildPtr(nodeId);
        //TODO: check and ensure bounds
        w |= childPtr&0xFFFFFF;

        z |= (flags&0xFF)<<24;
        w |= ((flags>>8)&0xFF)<<24;

        MemoryUtil.memPutInt(ptr, z); ptr += 4;
        MemoryUtil.memPutInt(ptr, w); ptr += 4;
    }

    public int getEndNodeId() {
        return this.allocationSet.getMaxIndex();
    }
    public int getNodeCount() {
        return this.allocationSet.getCount();
    }
}
