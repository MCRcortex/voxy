package me.cortex.voxy.common.util;

public class HierarchicalBitSet {
    private final int limit;
    private int cnt;
    //If a bit is 1 it means all children are also set
    private long A = 0;
    private final long[] B = new long[64];
    private final long[] C = new long[64*64];
    private final long[] D = new long[64*64*64];
    public HierarchicalBitSet(int limit) {//Fixed size of 64^4
        this.limit = limit;
        if (limit > (1<<(6*4))) {
            throw new IllegalArgumentException("Limit greater than capacity");
        }
    }

    public HierarchicalBitSet() {
        this(1<<(6*4));
    }

    public int allocateNext() {
        if (this.A==-1) {
            return -1;
        }
        if (this.cnt+1>this.limit) {
            return -2;//Limit reached
        }
        int idx = Long.numberOfTrailingZeros(~this.A);
        long bp = this.B[idx];
        idx = Long.numberOfTrailingZeros(~bp) + 64*idx;
        long cp = this.C[idx];
        idx = Long.numberOfTrailingZeros(~cp) + 64*idx;
        long dp = this.D[idx];
        idx =  Long.numberOfTrailingZeros(~dp) + 64*idx;
        int ret = idx;

        dp |= 1L<<(idx&0x3f);
        this.D[idx>>6] = dp;
        if (dp==-1) {
            idx >>= 6;
            cp |= 1L<<(idx&0x3f);
            this.C[idx>>6] = cp;
            if (cp==-1) {
                idx >>= 6;
                bp |= 1L<<(idx&0x3f);
                this.B[idx>>6] = bp;
                if (bp==-1) {
                    this.A |= 1L<<(idx&0x3f);
                }
            }
        }
        this.cnt++;

        return ret;
    }

    private void set(int idx) {
        long dp = this.D[idx>>6] |= 1L<<(idx&0x3f);
        if (dp==-1) {
            idx >>= 6;
            long cp = (this.C[idx>>6] |= 1L<<(idx&0x3f));
            if (cp==-1) {
                idx >>= 6;
                long bp = this.B[idx>>6] |= 1L<<(idx&0x3f);
                if (bp==-1) {
                    this.A |= 1L<<(idx&0x3f);
                }
            }
        }
        this.cnt++;
    }

    //Returns the next free index from idx
    private int findNextFree(int idx) {
        int pos = Long.numberOfTrailingZeros((~this.A)|((1L<<(idx>>18))-1));
        return 0;
    }

    public int allocateNextConsecutiveCounted(int count) {
        if (this.A==-1) {
            return -1;
        }
        if (this.cnt+count>this.limit) {
            return -2;//Limit reached
        }
        //TODO:FIXME DONT DO THIS, do a faster search

        int i = 0;
        while (true) {
            boolean isFree = true;
            for (int j = 0; j < count; j++) {
                if (this.isSet(i+j)) {
                    isFree = false;
                    break;
                }
            }

            if (isFree) {
                for (int j = 0; j < count; j++) {
                    this.set(j + i);
                }
                return i;
            } else {
                i++;//THIS IS SLOW BUT WORKS
                /* TODO: FIX AND FINISH OPTIMIZATION
                i +=
                while (this.D[i>>6] == -1) {
                    i++;
                }
                 */
            }
        }
    }


    public boolean free(int idx) {
        long v = this.D[idx>>6];
        boolean wasSet = (v&(1L<<(idx&0x3f)))!=0;
        this.cnt -= wasSet?1:0;
        this.D[idx>>6] = v&~(1L<<(idx&0x3f));
        idx >>= 6;
        this.C[idx>>6] &= ~(1L<<(idx&0x3f));
        idx >>= 6;
        this.B[idx>>6] &= ~(1L<<(idx&0x3f));
        idx >>= 6;
        this.A &= ~(1L<<(idx&0x3f));
        return wasSet;
    }

    public int getCount() {
        return this.cnt;
    }

    public boolean isSet(int idx) {
        return (this.D[idx>>6]&(1L<<(idx&0x3f)))!=0;
    }
}
