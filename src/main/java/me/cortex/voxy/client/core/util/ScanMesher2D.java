package me.cortex.voxy.client.core.util;

import java.util.Random;

public abstract class ScanMesher2D {

    private static final int MAX_SIZE = 16;

    // is much faster if implemented inline into parent
    private final long[] rowData = new long[32];
    private final int[] rowLength = new int[32];//How long down does a row entry go
    private final int[] rowDepth = new int[32];//How many rows does it cover
    private int rowBitset = 0;

    private int currentIndex = 0;
    private int currentSum = 0;
    private long currentData = 0;

    //Two different ways to do it, scanline then only merge on change, or try to merge with previous row at every step
    // or even can also attempt to merge previous but if the lengths are different split the current one and merge to previous
    public final void putNext(long data) {
        int idx = (this.currentIndex++)&31;//Mask to current row, but keep total so can compute actual indexing

        //If we are on the zero index, ignore it as we are going from empty state to maybe something state
        // setup data
        if (idx == 0) {
            //If the previous data is not zero, that means it was not merge-able, so emit it at the pos
            if (this.currentData!=0) {
                if ((this.rowBitset&(1<<31))!=0) {
                    this.emitQuad(31, ((this.currentIndex-1)>>5)-1, this.rowLength[31], this.rowDepth[31], this.rowData[31]);
                }
                this.rowBitset |= 1<<31;
                this.rowLength[31] = this.currentSum;
                this.rowDepth[31] = 1;
                this.rowData[31] = this.currentData;
            }

            //Set the data to the first element
            this.currentData = data;
            this.currentSum = 0;
        }

        //If we are different from previous (this can never happen if previous is index 0)
        if (data != this.currentData || this.currentSum == MAX_SIZE) {
            //write out previous data if its a non sentinel, it is guarenteed to not have a row bit set
            if (this.currentData != 0) {
                int prev = idx-1;//We need to write in the previous entry
                this.rowDepth[prev] = 1;
                this.rowLength[prev] = this.currentSum;
                this.rowData[prev] = this.currentData;
                this.rowBitset |= 1<<prev;
            }

            this.currentData = data;
            this.currentSum = 0;
        }
        this.currentSum++;


        boolean isSet = (this.rowBitset&(1<<idx))!=0;
        boolean causedByDepthMax = false;
        //Greadily merge with previous row if possible
        if (this.currentData != 0 &&//Ignore sentinel empty
                isSet &&
                this.rowLength[idx] == this.currentSum &&
                this.rowData[idx] == this.currentData) {//Can merge with previous row
            int depth = ++this.rowDepth[idx];
            this.currentSum = 0;//Clear sum since we went down
            this.currentData = 0;//Zero is sentinel value for absent
            if (depth != MAX_SIZE) {
                return;
            }
            causedByDepthMax = true;
        }

        if (isSet) {
            this.emitQuad(idx&31, ((this.currentIndex-1)>>5)-(causedByDepthMax?0:1), this.rowLength[idx], this.rowDepth[idx], this.rowData[idx]);
            this.rowBitset &= ~(1<<idx);
        }
    }

    //Emits quads that exist at the mask pos and clear
    private void emitRanged(int msk) {
        {//Emit quads that cover the previous indices
            int rowSet = this.rowBitset&msk;
            this.rowBitset &= ~msk;
            while (rowSet!=0) {//Need to emit quads that would have skipped, note that this does not include the current index
                int index = Integer.numberOfTrailingZeros(rowSet);
                rowSet &= ~Integer.lowestOneBit(rowSet);

                //Emit the quad, dont need to clear the data since it not existing in the bitmask is implicit no data
                this.emitQuad(index, (this.currentIndex>>5)-1, this.rowLength[index], this.rowDepth[index], this.rowData[index]);
            }
        }
    }

    public final void skip(int count) {
        /*
        if (count == 0) return;
        if (this.currentData != 0) {
            this.putNext(0); count--;
        }
        if (count != 0) {
            this.emitRanged(((1 << Math.min(count, 31)) - 1) << (this.currentIndex & 31));
        }
        this.currentIndex += count;
         */
        if (count == 0) return;
        if (this.currentData!=0) {
            this.putNext(0);
            count--;
        }
        if (0<count) {
            int msk = (int) ((1L<<Math.min(32, count))-1) << (this.currentIndex & 31);
            this.emitRanged(msk);
            this.currentIndex += count;
        }

    }

    public final void reset() {
        this.rowBitset = 0;
        this.currentSum = 0;
        this.currentData = 0;
        this.currentIndex = 0;
    }

    public final void endRow() {
        if ((this.currentIndex&31)!=0) {
            this.skip(32-(this.currentIndex&31));
        }
    }

    public final void finish() {
        /*
        if ((this.currentIndex&31)!=0) {
            this.skip(32-(this.currentIndex&31));
        } else {
            this.putNext(0);
            this.currentIndex--;//HACK to reset currentIndex&31 to 0
        }
        this.currentIndex++;
        for (int i = 0; i < 32; i++) {
            this.putNext(0);
        }*/
        //TODO: check this is correct
        if (this.currentIndex != 0) {
            this.skip(32 - (this.currentIndex & 31));
            this.emitRanged(-1);
        }

        this.reset();
    }

    protected abstract void emitQuad(int x, int z, int length, int width, long data);


    public static void main9(String[] args) {

        int[] qc = new int[3];
        var mesher = new ScanMesher2D(){
            @Override
            protected void emitQuad(int x, int z, int length, int width, long data) {
                qc[0]++;
                if (data != qc[0]) {
                    throw new IllegalStateException();
                }
                if (length*width != 1) {
                    if (data != qc[0])
                        throw new IllegalStateException();
                }
                if (x!=(((qc[0])&0x1f)))
                    throw new IllegalStateException();
                if (z!=((qc[0])>>5))
                    throw new IllegalStateException();
            }
        };

        mesher.putNext(0);
        int i = 1;
        while (true) {
            mesher.putNext(i++);
        }
    }

    public static void main2(String[] args) {
        long[] sample = new long[32*32];

        sample[0] = 1;
        sample[1] = 1;
        sample[2] = 1;
        sample[3] = 1;
        sample[4] = 2;
        sample[5] = 2;
        sample[6] = 2;
        sample[7] = 2;
        sample[0+32*1] = 1;
        sample[1+32*1] = 1;
        sample[2+32*1] = 1;
        sample[3+32*1] = 1;
        sample[4+32*1] = 2;
        sample[5+32*1] = 2;
        sample[6+32*1] = 2;
        sample[7+32*1] = 2;
        sample[31+32*0] = 6;
        sample[31+32*1] = 6;
        sample[30+32*2] = 7;
        sample[31+32*2] = 7;
        sample[30+32*3] = 7;
        sample[31+32*3] = 7;
        sample[31+32*8] = 8;
        var mesher = new ScanMesher2D() {
            @Override
            protected void emitQuad(int x, int z, int length, int width, long data) {
                System.out.println(length + ", " + width + ", " + data);
            }
        };
        int j = 0;
        for (long i : sample) {
            if (j%32 == 0) {
                System.out.println("row");
            }
            mesher.putNext(i);
            j++;
        }
    }

    public static void main6(String[] args) {
        var r = new Random(0);
        float DENSITY = 0.90f;
        int RANGE = 3;
        while (true) {
            long[] data = new long[32*32];
            for (int i = 0; i < data.length; i++) {
                data[i] =  r.nextFloat()<DENSITY?(r.nextInt(RANGE)+1):0;
            }
            long[] out = new long[32*32];
            var mesher = new ScanMesher2D(){

                @Override
                protected void emitQuad(int x, int z, int length, int width, long data) {
                    if (data == 0) {
                        throw new IllegalStateException();
                    }
                    if (z<0||x<0||x>31||z>31) {
                        throw new IllegalStateException();
                    }
                    if (length<1||width<1||length>16||width>16) {
                        throw new IllegalStateException();
                    }
                    x -= length-1;
                    z -= width-1;
                    if (z<0||x<0||x>31||z>31) {
                        throw new IllegalStateException();
                    }
                    for (int X = x; X < x+length; X++) {
                        for (int Z = z; Z < z+width; Z++) {
                            int idx = Z*32+X;
                            if (out[idx] != 0) {
                                throw new IllegalStateException();
                            }
                            out[idx] = data;
                        }
                    }
                }
            };

            for (long a : data) {
                mesher.putNext(a);
            }
            mesher.finish();

            for (int i = 0; i < 32*32; i++) {
                if (data[i] != out[i]) {
                    System.out.println("ERROR");
                }
            }
        }
    }

    public static void main(String[] args) {
        long[] data = new long[32*32];

        for (int x = 0; x < 20; x++) {
            for (int z = 0; z < 20; z++) {
                data[z*32+x] = 1;
            }
        }
        long[] out = new long[32*32];
        var mesher = new ScanMesher2D(){

            @Override
            protected void emitQuad(int x, int z, int length, int width, long data) {
                if (data == 0) {
                    throw new IllegalStateException();
                }
                if (z<0||x<0||x>31||z>31) {
                    throw new IllegalStateException();
                }
                if (length<1||width<1||length>16||width>16) {
                    throw new IllegalStateException();
                }
                x -= length-1;
                z -= width-1;
                if (z<0||x<0||x>31||z>31) {
                    throw new IllegalStateException();
                }
                for (int X = x; X < x+length; X++) {
                    for (int Z = z; Z < z+width; Z++) {
                        int idx = Z*32+X;
                        if (out[idx] != 0) {
                            throw new IllegalStateException();
                        }
                        out[idx] = data;
                    }
                }
            }
        };

        for (long a : data) {
            mesher.putNext(a);
        }
        mesher.finish();

        for (int i = 0; i < 32*32; i++) {
            if (data[i] != out[i]) {
                System.out.println("ERROR");
            }
        }
    }
}
