package me.cortex.voxy.client.core.util;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Random;

//TODO: redo this so that it works as you are inserting data into it maybe? since it should be much faster??

public final class Mesher2D {
    private final int size;
    private final int maxSize;
    private final long[] data;
    private final long[] setset;
    private int[] quadCache;
    private boolean isEmpty = true;
    private int setsMsk = 0;
    public Mesher2D(int sizeBits, int maxSize) {
        if (sizeBits > 5) {
            throw new IllegalStateException("Due to the addition of the setsMsk, size greter than 32 is not supported atm");
        }

        this.size = sizeBits;
        this.maxSize = maxSize;
        this.data = new long[1<<(sizeBits<<1)];
        this.setset = new long[(1<<(sizeBits<<1))>>6];
        this.quadCache = new int[128];
    }

    private int getIdx(int x, int z) {
        int M = (1<<this.size)-1;
        if (false&&(x>M || z>M)) {
            throw new IllegalStateException();
        }
        return ((z&M)<<this.size)|(x&M);
    }

    public Mesher2D put(int x, int z, long data) {
        this.isEmpty = false;
        int idx = this.getIdx(x, z);
        this.data[idx] = data;
        this.setset[idx>>6] |= 1L<<(idx&0b111111);
        this.setsMsk |= 1<<(idx>>6);
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
        return (this.setset[id>>6]&(1L<<(id&0b111111))) != 0 && this.data[id] == match;
    }

    private int nextSetBit(int base) {
        int wPos = Integer.numberOfTrailingZeros(this.setsMsk>>>(base>>6))+(base>>6);
        while (wPos != 16) {
            long word = this.setset[wPos++];
            if (word != 0) {
                return Long.numberOfTrailingZeros(word) + ((wPos-1)<<6);
            }
        }
        return -1;
    }

    //Returns the number of compacted quads
    public int process() {
        if (this.isEmpty) {
            return 0;
        }

        int[] quads = this.quadCache;
        int idxCount = 0;

        //TODO: add different strategies/ways to mesh
        int posId = this.data[0] == 0?this.nextSetBit(0):0;
        while (posId < this.data.length && posId != -1) {
            int idx = posId;
            long data = this.data[idx];

            int M = (1<<this.size)-1;
            int x = idx&M;
            int z = (idx>>>this.size)&M;

            boolean ex = x != ((1<<this.size)-1);
            boolean ez = z != ((1<<this.size)-1);
            int endX = x;
            int endZ = z;
            while (ex || ez) {
                //Expand in the x direction
                if (ex) {
                    if (endX - x >= this.maxSize || endX >= (1 << this.size) - 1) {
                        ex = false;
                    }
                }
                if (ex) {
                    for (int tz = z; tz < endZ+1; tz++) {
                        if (!this.canMerge(endX + 1, tz, data)) {
                            ex = false;
                            break;
                        }
                    }
                }
                if (ex) {
                    endX++;
                }
                if (ez) {
                    if (endZ - z >= this.maxSize || endZ >= (1<<this.size)-1) {
                        ez = false;
                    }
                }
                if (ez) {
                    for (int tx = x; tx < endX+1; tx++) {
                        if (!this.canMerge(tx, endZ + 1, data)) {
                            ez = false;
                            break;
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
                    int cid = this.getIdx(mx, mz);
                    this.setset[cid>>6] &= ~(1L<<(cid&0b111111));
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
            posId = this.nextSetBit(posId);
        }

        this.quadCache = quads;
        return idxCount;
    }

    public int[] getArray() {
        return this.quadCache;
    }

    public void reset() {
        if (!this.isEmpty) {
            this.isEmpty = true;
            this.setsMsk = 0;
            Arrays.fill(this.setset, 0);
            Arrays.fill(this.data, 0);
        }
    }

    public long getDataFromQuad(int quad) {
        return this.getData(getX(quad), getZ(quad));
    }

    public long getData(int x, int z) {
        return this.data[this.getIdx(x, z)];
    }

    public static void main3(String[] args) {
        var mesh = new Mesher2D(5,15);
        mesh.put(30,30, 123);
        mesh.put(31,30, 123);
        mesh.put(30,31, 123);
        mesh.put(31,31, 123);
        int count = mesh.process();

        System.err.println(count);
    }

    public static void main(String[] args) {
        var r = new Random(123451);
        var mesh = new Mesher2D(5,15);
        /*
        for (int j = 0; j < 512; j++) {
            mesh.put(r.nextInt(32), r.nextInt(32), r.nextInt(10));
        }
         */
        int cnt = 0;
        for (int i = 0; i < 12000; i++) {
            for (int j = 0; j < 512; j++) {
                mesh.put(r.nextInt(32), r.nextInt(32), r.nextInt(32));
            }
            cnt += mesh.process();
            mesh.reset();
        }
        cnt = 0;
        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 15; z++) {
                    mesh.put(x, z, 134);
                }
            }
            mesh.put(31, 31, 134);
            cnt += mesh.process();
            mesh.reset();
        }
        System.err.println(cnt);
        System.err.println(System.currentTimeMillis()-start);
        var dat = mesh.getArray();
        //for (int i = 0; i < cnt; i++) {
        //    var q = dat[i];
        //    System.err.println("X: " + getX(q) + " Z: " + getZ(q) + " W: " + getW(q) + " H: " + getH(q));
        //}
    }

    public static void main2(String[] args) {
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
            a += result;
        }
        System.out.println(total/(1e+6));
        System.out.println((double) (total/(1e+6))/200000);
        //mesh.put(0,0,1);
    }
}

