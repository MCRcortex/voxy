package me.cortex.voxy.client.core.rendering.hierachical2;

import me.cortex.voxy.common.util.HierarchicalBitSet;

public final class NodeStore {
    private final HierarchicalBitSet allocationSet;
    private final long[] localNodeData;
    public NodeStore(int maxNodeCount) {
        this.localNodeData = new long[maxNodeCount*4];
        this.allocationSet = new HierarchicalBitSet(maxNodeCount);
    }

    public long nodePosition(int nodeId) {
        return this.localNodeData[nodeId<<2];
    }

    public boolean nodeExists(int nodeId) {
        return false;
    }


    public void markRequestInFlight(int nodeId) {

    }

    public boolean nodeRequestInFlight(int nodeId) {
        return false;
    }

    public boolean isLeafNode(int nodeId) {
        return false;
    }

}
