package me.cortex.voxy.client.core.rendering.building;

import me.cortex.voxy.client.core.util.ExpansionUtil;
import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Random;

//Block occupancy, 2 lvl compacted bitset for occluding block existance
// TODO: need to add neighboring chunk data aswell? or somehow do a linking thing where this is stored in a secondary storage
//  where we can link them together (or store the neighbor faces seperately or something) might be out of scope for this class
public class OccupancySet {
    private long topLvl;//4x4x4
    private final long[] bottomLvl = new long[(4*4*4)*8];
    public void set(final int pos) {
        final long topBit = 1L<< ExpansionUtil.compress(pos, 0b11000_11000_11000);
        final int  botIdx =     ExpansionUtil.compress(pos, 0b00111_00111_00111);

        int baseBotIdx = Long.bitCount(this.topLvl&(topBit-1))*8;
        if ((this.topLvl & topBit) == 0) {
            //we need to shuffle up all the bottomlvl
            long toMove = this.topLvl & (~((topBit << 1) - 1));
            if (toMove != 0) {
                int base = baseBotIdx+8;//+8 cause were bubbling
                int count = Long.bitCount(toMove);
                for (int i = base+count*8-1; base<=i; i--) {
                    this.bottomLvl[i] = this.bottomLvl[i-8];
                }
                for (int i = baseBotIdx; i<baseBotIdx+8; i++) {
                    this.bottomLvl[i] = 0;
                }
            }

            this.topLvl |= topBit;
        }

        this.bottomLvl[baseBotIdx+(botIdx>>6)] |= 1L<<(botIdx&63);
    }

    private boolean get(int pos) {
        final long topBit = 1L<< ExpansionUtil.compress(pos, 0b11000_11000_11000);
        final int  botIdx =     ExpansionUtil.compress(pos, 0b00111_00111_00111);
        if ((this.topLvl & topBit) == 0) {
            return false;
        }
        int baseBotIdx = Long.bitCount(this.topLvl&(topBit-1))*8;

        return (this.bottomLvl[baseBotIdx+(botIdx>>6)]&(1L<<(botIdx&63)))!=0;
    }

    public void reset() {
        if (this.topLvl != 0) {
            Arrays.fill(this.bottomLvl, 0);
        }
        this.topLvl = 0;
    }

    public int writeSize() {
        return 8+Long.bitCount(this.topLvl)*8*8;
    }

    public boolean isEmpty() {
        return this.topLvl == 0;
    }

    public void write(long ptr, boolean asLongs) {
        if (asLongs) {
            MemoryUtil.memPutLong(ptr, this.topLvl); ptr += 8;
            int cnt = Long.bitCount(this.topLvl);
            for (int i = 0; i < cnt; i++) {
                for (int j = 0; j < 8; j++) {
                    MemoryUtil.memPutLong(ptr, this.bottomLvl[i*8+j]); ptr += 8;
                }
            }
        } else {
            MemoryUtil.memPutInt(ptr, (int) (this.topLvl>>>32)); ptr += 4;
            MemoryUtil.memPutInt(ptr, (int) this.topLvl); ptr += 4;
            int cnt = Long.bitCount(this.topLvl);
            for (int i = 0; i < cnt; i++) {
                for (int j = 0; j < 8; j++) {
                    long v = this.bottomLvl[i*8+j];
                    MemoryUtil.memPutInt(ptr, (int) (v>>>32)); ptr += 4;
                    MemoryUtil.memPutInt(ptr, (int) v); ptr += 4;
                }
            }
        }
    }

    public static void main(String[] args) {
        for (int q = 0; q < 1000; q++) {
            var o = new OccupancySet();
            var r = new Random(12523532643L*q);
            var bs = new BitSet(32 * 32 * 32);
            for (int i = 0; i < 5000; i++) {
                int p = r.nextInt(32 * 32 * 32);
                o.set(p);
                bs.set(p);

                for (int j = 0; j < 32 * 32 * 32; j++) {
                    if (o.get(j) != bs.get(j)) throw new IllegalStateException();
                }
            }
        }
    }
}
