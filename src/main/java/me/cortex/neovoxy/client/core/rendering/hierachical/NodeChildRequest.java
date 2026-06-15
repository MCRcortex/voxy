package me.cortex.neovoxy.client.core.rendering.hierachical;

class NodeChildRequest {
    //Child states contain micrometadata in the top bits
    // such as isEmpty, and isEmptyButEventuallyHasNonEmptyChild
    private final long nodePos;

    private final int[] childStates = new int[]{-1,-1,-1,-1,-1,-1,-1,-1};
    private final byte[] childChildExistence = new byte[]{(byte) 0,(byte) 0,(byte) 0,(byte) 0,(byte) 0,(byte) 0,(byte) 0,(byte) 0};

    private byte results;
    private byte mask;
    private byte existenceMask = 0;

    NodeChildRequest(long nodePos) {
        this.nodePos = nodePos;
    }

    public int getChildMesh(int childIdx) {
        if ((this.mask&(1<<childIdx))==0) {
            throw new IllegalStateException("Tried getting mesh result of child not in mask");
        }
        return this.childStates[childIdx];
    }

    public void setChildChildExistence(int childIdx, byte childExistence) {
        if ((this.mask&(1<<childIdx))==0) {
            throw new IllegalStateException("Tried setting child child existence in request when child isnt in mask");
        }
        this.childChildExistence[childIdx] = childExistence;
        this.existenceMask |= (byte) (1<<childIdx);
    }

    public boolean hasChildChildExistence(int childId) {
        if ((this.mask&(1<<childId))==0) {
            throw new IllegalStateException("Tried getting child child existence set of child not in mask");
        }
        return (this.existenceMask&(1<<childId))!=0;
    }

    public byte getChildChildExistence(int childIdx) {
        if (!this.hasChildChildExistence(childIdx)) {
            throw new IllegalStateException("Tried getting child child existence when child child existence for child was not set");
        }
        return this.childChildExistence[childIdx];
    }

    public int setChildMesh(int childIdx, int mesh) {
        if ((this.mask&(1<<childIdx))==0) {
            throw new IllegalStateException("Tried setting child mesh when child isnt in mask");
        }
        //Note the mesh can be -ve meaning empty mesh, but we should still mark that node as having a result
        boolean isFirstInsert = (this.results&(1<<childIdx))==0;
        this.results |= (byte) (1<<childIdx);

        int prev = this.childStates[childIdx];
        this.childStates[childIdx] = mesh;
        if (isFirstInsert) {
            return -1;
        } else {
            return prev;
        }
    }

    public int removeAndUnRequire(int childIdx) {
        byte MSK = (byte) (1<<childIdx);
        if ((this.mask&MSK)==0) {
            throw new IllegalStateException("Tried removing and unmasking child that was never masked");
        }
        byte prev = this.results;
        this.results &= (byte) ~MSK;
        this.mask &= (byte) ~MSK;
        this.existenceMask &= (byte) ~MSK;
        int mesh = this.childStates[childIdx];
        this.childStates[childIdx] = -1;
        if ((prev&MSK)==0) {
            return -1;
        } else {
            return mesh;
        }
    }

    public void addChildRequirement(int childIdx) {
        byte MSK = (byte) (1<<childIdx);
        if ((this.mask&MSK)!=0) {
            throw new IllegalStateException("Child already required!");
        }
        this.mask |= MSK;
    }

    public boolean isSatisfied() {
        return (this.results&this.mask)==this.mask;
    }

    public long getPosition() {
        return this.nodePos;
    }

    public byte getMsk() {
        return this.mask;
    }

}
