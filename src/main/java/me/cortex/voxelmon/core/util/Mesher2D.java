package me.cortex.voxelmon.core.util;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Random;

public class Mesher2D {
    private final int size;
    private final int maxSize;
    private final long[] data;
    private final BitSet meshed;
    private int[] quadCache;
    public Mesher2D(int sizeBits, int maxSize) {
        this.size = sizeBits;
        this.maxSize = maxSize;
        this.data = new long[1<<(sizeBits<<1)];
        this.meshed = new BitSet(1<<(sizeBits<<1));
        this.quadCache = new int[128];
    }

    private int getIdx(int x, int z) {
        int M = (1<<this.size)-1;
        if (x>M || z>M) {
            throw new IllegalStateException();
        }
        return ((z&M)<<this.size)|(x&M);
    }

    public Mesher2D put(int x, int z, long data) {
        this.data[this.getIdx(x, z)] = data;
        return this;
    }

    public static int getX(int data) {
        return (data>>24)&0xFF;
    }

    public static int getZ(int data) {
        return (data>>16)&0xFF;
    }

    public static int getH(int data) {
        return (data>>8)&0xFF;
    }

    public static int getW(int data) {
        return data&0xFF;
    }

    //
    private static int encodeQuad(int x, int z, int sx, int sz) {
        return ((x&0xFF)<<24)|((z&0xFF)<<16)|((sx&0xFF)<<8)|((sz&0xFF)<<0);
    }

    private boolean canMerge(int x, int z, long match) {
        int id = this.getIdx(x, z);
        return this.data[id] == match && !this.meshed.get(id);
    }

    public int[] process() {
        //TODO: replace this loop with a loop over a bitset of data that has been put into the mesher, have the this.meshed be removed
        // and just clear the databitset when its meshed

        int[] quads = this.quadCache;
        int idxCount = 0;

        //TODO: add different strategies/ways to mesh
        for (int z = 0; z < 1<<this.size; z++) {
            for (int x = 0; x < 1<<this.size; x++) {
                int idx = this.getIdx(x,z);
                long data = this.data[idx];
                if (data == 0 || this.meshed.get(idx)) {
                    //if this position has been meshed or is empty, dont mesh and continue
                    continue;
                }


                boolean ex = x != ((1<<this.size)-1);
                boolean ez = z != ((1<<this.size)-1);
                int endX = x;
                int endZ = z;
                while (ex || ez) {
                    //Expand in the x direction
                    if (ex) {
                        if (endX + 1 > this.maxSize || endX+1 == (1 << this.size) - 1) {
                            ex = false;
                        }
                    }
                    if (ex) {
                        for (int tz = z; tz < endZ+1; tz++) {
                            if (!this.canMerge(endX + 1, tz, data)) {
                                ex = false;
                            }
                        }
                    }
                    if (ex) {
                        endX++;
                    }
                    if (ez) {
                        if (endZ + 1 > this.maxSize || endZ+1 == (1<<this.size)-1) {
                            ez = false;
                        }
                    }
                    if (ez) {
                        for (int tx = x; tx < endX+1; tx++) {
                            if (!this.canMerge(tx, endZ + 1, data)) {
                                ez = false;
                            }
                        }
                    }
                    if (ez) {
                        endZ++;
                    }
                }

                //Mark the sections as meshed
                for (int mx = x; mx <= endX; mx++) {
                    for (int mz = z; mz <= endZ; mz++) {
                        this.meshed.set(this.getIdx(mx, mz));
                    }
                }

                int encodedQuad = encodeQuad(x, z, endX - x + 1, endZ - z + 1);

                {
                    int pIdx = idxCount++;
                    if (pIdx == quads.length) {
                        var newArray = new int[quads.length + 64];
                        System.arraycopy(quads, 0, newArray, 0, quads.length);
                        quads = newArray;
                    }
                    quads[pIdx] = encodedQuad;
                }
            }
        }

        var out = new int[idxCount];
        System.arraycopy(quads, 0, out, 0, idxCount);
        this.quadCache = quads;
        return out;
    }

    public static void main(String[] args) {
        var r = new Random(123451);
        int a = 0;
        long total = 0;
        for (int i = 0; i < 200000; i++) {
            var mesh = new Mesher2D(5,16);
            for (int j = 0; j < 512; j++) {
                mesh.put(r.nextInt(32), r.nextInt(32), r.nextInt(100));
            }
            long s = System.nanoTime();
            var result = mesh.process();
            total += System.nanoTime() - s;
            a += result.hashCode();
        }
        System.out.println(total/(1e+6));
        System.out.println((double) (total/(1e+6))/200000);
        //mesh.put(0,0,1);
    }

    public void reset() {
        this.meshed.clear();
        Arrays.fill(this.data, 0);
    }

    public long getDataFromQuad(int quad) {
        return this.getData(getX(quad), getZ(quad));
    }

    public long getData(int x, int z) {
        return this.data[this.getIdx(x, z)];
    }
}

