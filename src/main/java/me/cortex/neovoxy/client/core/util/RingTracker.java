package me.cortex.neovoxy.client.core.util;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import me.cortex.neovoxy.common.Logger;

import java.util.Random;

//Tracks a ring and load/unload positions
// can process N of these load/unload positions
public class RingTracker {
    //TODO: replace with custom map that removes elements if its mapped to 0
    private final Long2ByteOpenHashMap operations = new Long2ByteOpenHashMap(1<<13);
    private final int[] boundDist;
    private final int radius;
    private int centerX;
    private int centerZ;

    public RingTracker(int radius, int centerX, int centerZ, boolean fill) {
        this(null, radius, centerX, centerZ, fill);
    }

    public RingTracker(RingTracker stealFrom, int radius, int centerX, int centerZ, boolean fill) {
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        this.boundDist = generateBoundingHalfCircleDistance(radius);
        if (stealFrom != null) {
            this.operations.putAll(stealFrom.operations);
            stealFrom.operations.clear();
        }
        if (fill) {
            this.fillRing(true);
        }
    }

    private static long pack(int x, int z) {
        return Integer.toUnsignedLong(x)|(Integer.toUnsignedLong(z)<<32);
    }

    private void fillRing(boolean load) {
        for (int i = 0; i <= this.radius*2; i++) {
            int x = this.centerX + i - this.radius;
            int d = this.boundDist[i];
            for (int z = this.centerZ-d; z <= this.centerZ+d; z++) {
                int res = this.operations.addTo(pack(x, z), (byte) (load?1:-1));
                if ((load&&0<res)||(((!load)&&res<0))) {
                    throw new IllegalStateException();
                }
            }
        }
    }

    public void unload() {
        this.fillRing(false);
    }

    //Moves the center from old to new and updates the operations map
    public void moveCenter(int x, int z) {
        //TODO, if the new center is greater than radius from current, unload all current and load all at new
        if (this.radius+1<Math.abs(x-this.centerX) || this.radius+1<Math.abs(z-this.centerZ)) {
            this.fillRing(false);
            this.centerX = x;
            this.centerZ = z;
            this.fillRing(true);
        } else {
            if (x != this.centerX) {
                moveX(x - this.centerX);
            }
            if (z != this.centerZ) {
                moveZ(z - this.centerZ);
            }
        }
    }

    private void moveZ(int delta) {
        if (delta == 0) return;
        //Since +- 1 is the most common operation, fastpath it
        if (delta == -1 || delta == 1) {
            for (int i = 0; i <= this.radius * 2; i++) {
                int x = this.centerX + i - this.radius;
                //Multiply by the delta since its +-1 it also then makes it the correct orientation
                int d = this.boundDist[i]*delta;
                int pz = this.centerZ+d+delta;//Point to add (we need to offset by 1 in the mov direction)
                int nz = this.centerZ-d;//Point to rem
                if (0<this.operations.addTo(pack(x, pz), (byte) 1))//Load point
                    throw new IllegalStateException("x: "+x+", z: "+pz+" state: "+this.operations.get(pack(x, pz)));
                if (this.operations.addTo(pack(x, nz), (byte) -1)<0)//Unload point
                    throw new IllegalStateException("x: "+x+", z: "+nz+" state: "+this.operations.get(pack(x, nz)));
            }
            this.centerZ += delta;
        } else {
            int sDelta = Integer.signum(delta);
            for (int i = 0; i <= this.radius * 2; i++) {
                int x = this.centerX + i - this.radius;
                //Multiply by the delta since its +-1 it also then makes it the correct orientation
                int d = this.boundDist[i]*sDelta;
                int pz = this.centerZ+d;//Point to add (we need to offset by 1 in the mov direction)
                for (int z = pz + (sDelta<0?delta:1); z <= pz + (sDelta<0?-1:delta); z++) {
                    if (0<this.operations.addTo(pack(x, z), (byte) 1))//Load point
                        throw new IllegalStateException();
                }
                int nz = this.centerZ-d;//Point to rem
                for (int z = nz + (sDelta<0?(delta+1):0); z < nz + (sDelta<0?1:delta); z++) {
                    if (this.operations.addTo(pack(x, z), (byte) -1)<0)//Unload point
                        throw new IllegalStateException();
                }
            }
            this.centerZ += delta;
        }
    }

    private void moveX(int delta) {
        if (delta == 0) return;
        //Since +- 1 is the most common operation, fastpath it
        if (delta == -1 || delta == 1) {
            for (int i = 0; i <= this.radius * 2; i++) {
                int z = this.centerZ + i - this.radius;
                //Multiply by the delta since its +-1 it also then makes it the correct orientation
                int d = this.boundDist[i]*delta;
                int px = this.centerX+d+delta;//Point to add (we need to offset by 1 in the mov direction)
                int nx = this.centerX-d;//Point to rem
                if (0<this.operations.addTo(pack(px, z), (byte) 1))//Load point
                    throw new IllegalStateException();
                if (this.operations.addTo(pack(nx, z), (byte) -1)<0)//Unload point
                    throw new IllegalStateException();
            }
            this.centerX += delta;
        } else {
            int sDelta = Integer.signum(delta);
            for (int i = 0; i <= this.radius * 2; i++) {
                int z = this.centerZ + i - this.radius;
                //Multiply by the delta since its +-1 it also then makes it the correct orientation
                int d = this.boundDist[i]*sDelta;
                int px = this.centerX+d;//Point to add (we need to offset by 1 in the mov direction)
                for (int x = px + (sDelta<0?delta:1); x <= px + (sDelta<0?-1:delta); x++) {
                    if (0<this.operations.addTo(pack(x, z), (byte) 1))//Load point
                        throw new IllegalStateException();
                }
                int nx = this.centerX-d;//Point to rem
                for (int x = nx + (sDelta<0?(delta+1):0); x < nx + (sDelta<0?1:delta); x++) {
                    if (this.operations.addTo(pack(x, z), (byte) -1)<0)//Unload point
                        throw new IllegalStateException();
                }
            }
            this.centerX += delta;
        }
    }

    public interface IUpdateConsumer {
        void accept(int x, int z);
    }

    //Processes N operations from the operations map
    public int process(int N, IUpdateConsumer onAdd, IUpdateConsumer onRemove) {
        if (this.operations.isEmpty()) {
            return 0;
        }
        var iter = this.operations.long2ByteEntrySet().fastIterator();
        int i = 0;
        while (iter.hasNext() && N--!=0) {
            var entry = iter.next();
            if (entry.getByteValue()==0) {
                iter.remove(); N++;
                continue;
            }
            i++;
            byte op = entry.getByteValue();
            if (op != 1 && op != -1) {
                throw new IllegalStateException();
            }
            boolean isAdd = op == 1;
            long pos = entry.getLongKey();
            int x = (int) (pos&0xFFFFFFFFL);
            int z = (int) ((pos>>>32)&0xFFFFFFFFL);
            if (isAdd) {
                onAdd.accept(x, z);
            } else {
                onRemove.accept(x, z);
            }
            iter.remove();
        }
        return i;
    }

    private int[] generateBoundingHalfCircleDistance(int radius) {
        var ret = new int[radius*2+1];
        for (int i = -radius; i <= radius; i++) {
            ret[i+radius] = (int)Math.sqrt(radius*radius - i*i);
        }
        return ret;
    }

    public static void main(String[] args) {
        for (int j = 0; j < 50; j++) {
            Random r = new Random((j+18723)*1234);
            var tracker = new RingTracker(r.nextInt(100)+1, 0, 0, true);
            int R = r.nextInt(500);
            for (int i = 0; i < 50_000; i++) {
                int x = r.nextInt(R*2+1)-R;
                int z = r.nextInt(R*2+1)-R;
                tracker.moveCenter(x, z);
            }
            tracker.fillRing(false);
            tracker.process(64, (x,z)->{
                Logger.info("Add:", x,",",z);
            }, (x,z)->{
                Logger.info("Remove:", x,",",z);
            });
        }

    }
}
