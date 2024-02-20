package me.cortex.voxy.client.core;

//Contains the logic to determine what is loaded and at what LoD level, dispatches render changes
// also determines what faces are built etc

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import me.cortex.voxy.client.core.rendering.RenderTracker;
import me.cortex.voxy.client.core.util.RingUtil;
import net.minecraft.client.MinecraftClient;

import java.util.stream.IntStream;

//Can use ring logic
// i.e. when a player moves the rings of each lod change (how it was doing in the original attempt)
// also have it do directional quad culling and rebuild the chunk if needed (this shouldent happen very often) (the reason is to significantly reduce draw calls)
// make the rebuild range like +-5 chunks along each axis (that means at higher levels, should only need to rebuild like)
// 4 sections or something
public class DistanceTracker {
    private final TransitionRing2D[] loDRings;
    private final TransitionRing2D[] cacheLoadRings;
    private final TransitionRing2D[] cacheUnloadRings;
    private final RenderTracker tracker;
    private final int minYSection;
    private final int maxYSection;

    public DistanceTracker(RenderTracker tracker, int[] lodRingScales, int cacheLoadDistance, int cacheUnloadDistance) {
        this.loDRings = new TransitionRing2D[lodRingScales.length];
        this.cacheLoadRings = new TransitionRing2D[lodRingScales.length];
        this.cacheUnloadRings = new TransitionRing2D[lodRingScales.length];
        this.tracker = tracker;
        this.minYSection = MinecraftClient.getInstance().world.getBottomSectionCoord()/2;//-128;
        this.maxYSection = MinecraftClient.getInstance().world.getTopSectionCoord()/2;//128;


        //The rings 0+ start at 64 vanilla rd, no matter what the game is set at, that is if the game is set to 32 rd
        // there will still be 32 chunks untill the first lod drop
        // if the game is set to 16, then there will be 48 chunks until the drop
        for (int i = 0; i < this.loDRings.length; i++) {
            //TODO: FIXME: check that the level shift is right when inc/dec
            int capRing = i;
            this.loDRings[i] = new TransitionRing2D(6+i, lodRingScales[i], (x, z) -> this.dec(capRing+1, x, z), (x, z) -> this.inc(capRing+1, x, z));

            //TODO:FIXME i think the radius is wrong and (lodRingScales[i]) needs to be (lodRingScales[i]<<1) since the transition ring (the thing above)
            // acts on LoD level + 1

            //TODO: check this is actually working lmao and make it generate parent level lods on the exit instead of entry so it looks correct when flying backwards
            this.cacheLoadRings[i] = new TransitionRing2D(5+i, (lodRingScales[i]<<1) + cacheLoadDistance, (x, z) -> {
                //When entering a cache ring, trigger a mesh op and inject into cache
                for (int y = this.minYSection>>capRing; y <= this.maxYSection>>capRing; y++) {
                    this.tracker.addCache(capRing, x, y, z);
                }
            }, (x, z) -> {});
            this.cacheUnloadRings[i] = new TransitionRing2D(5+i, (lodRingScales[i]<<1) + cacheUnloadDistance, (x, z) -> {}, (x, z) -> {
                //When exiting the cache unload ring, tell the cache to dump whatever mesh it has cached and not add any mesh from that position
                for (int y = this.minYSection>>capRing; y <= this.maxYSection>>capRing; y++) {
                    this.tracker.removeCache(capRing, x, y, z);
                }
            });
        }
    }

    private void inc(int lvl, int x, int z) {
        for (int y = this.minYSection>>lvl; y <= this.maxYSection>>lvl; y++) {
            this.tracker.inc(lvl, x, y, z);
        }
    }

    private void dec(int lvl, int x, int z) {
        for (int y = this.minYSection>>lvl; y <= this.maxYSection>>lvl; y++) {
            this.tracker.dec(lvl, x, y, z);
        }
    }

    //How it works is there are N ring zones (one zone for each lod boundary)
    // the transition zone is what determines what lods are rendered etc (and it biases higher lod levels cause its easier)
    // the transition zone is only ever checked when the player moves 1<<(4+lodlvl) blocks, its position is set

    //if the center suddenly changes (say more than 1<<(7+lodlvl) block) then invalidate the entire ring and recompute
    // the lod sections
    public void setCenter(int x, int y, int z) {
        for (var ring : this.cacheLoadRings) {
            if (ring!=null)
                ring.update(x, z);
        }
        //Update in reverse order (biggest lod to smallest lod)
        for (int i = this.loDRings.length-1; -1<i; i-- ) {
            this.loDRings[i].update(x, z);
        }
        for (var ring : this.cacheUnloadRings) {
            if (ring!=null)
                ring.update(x, z);
        }
    }

    public void init(int x, int z) {
        for (var ring : this.cacheLoadRings) {
            if (ring != null)
                ring.setCenter(x, z);
        }

        for (var ring : this.cacheUnloadRings) {
            if (ring != null)
                ring.setCenter(x, z);
        }

        for (var ring : this.loDRings) {
            if (ring != null)
                ring.setCenter(x, z);
        }

        var thread = new Thread(()-> {
            //Radius of chunks to enqueue
            int SIZE = 128;
            //Insert highest LOD level
            for (int ox = -SIZE; ox <= SIZE; ox++) {
                for (int oz = -SIZE; oz <= SIZE; oz++) {
                    this.inc(4, (x >> (5 + this.loDRings.length)) + ox, (z >> (5 + this.loDRings.length)) + oz);
                }
            }


            for (var ring : this.cacheLoadRings) {
                if (ring != null)
                    ring.fill(x, z);
            }

            for (var ring : this.cacheUnloadRings) {
                if (ring != null)
                    ring.fill(x, z);
            }

            for (int i = this.loDRings.length - 1; 0 <= i; i--) {
                if (this.loDRings[i] != null) {
                    this.loDRings[i].fill(x, z);
                }
            }
        });
        thread.setName("LoD Ring Initializer");
        thread.start();
        //TODO: FIXME: need to destory on shutdown
    }


    //TODO: add a new class thing that can track the central axis point so that
    // geometry can be rebuilt with new flags with correct facing geometry built
    // (could also make it so that it emits 3x the amount of draw calls, but that seems very bad idea)


    private interface Transition2DCallback {
        void callback(int x, int z);
    }
    private static final class TransitionRing2D {
        private final int triggerRangeSquared;
        private final int shiftSize;
        private final Transition2DCallback enter;
        private final Transition2DCallback exit;
        private final int[] cornerPoints;
        private final int radius;

        private int lastUpdateX;
        private int lastUpdateZ;

        private int currentX;
        private int currentZ;

        //Note radius is in shiftScale
        private TransitionRing2D(int shiftSize, int radius, Transition2DCallback onEntry, Transition2DCallback onExit) {
            this(shiftSize, radius, onEntry, onExit, 0, 0, 0);
        }
        private TransitionRing2D(int shiftSize, int radius, Transition2DCallback onEntry, Transition2DCallback onExit, int ix, int iy, int iz) {
            //trigger just less than every shiftSize scale
            this.triggerRangeSquared = 1<<((shiftSize<<1) - 1);
            this.shiftSize = shiftSize;
            this.enter = onEntry;
            this.exit = onExit;
            this.cornerPoints = RingUtil.generatingBoundingCorner2D(radius);
            this.radius = radius;
        }

        private long Prel(int x, int z) {
            return (Integer.toUnsignedLong(this.currentZ + z)<<32)|Integer.toUnsignedLong(this.currentX + x);
        }

        public void update(int x, int z) {
            long dx = this.lastUpdateX - x;
            long dz = this.lastUpdateZ - z;
            long distSquared =  dx*dx + dz*dz;
            if (distSquared < this.triggerRangeSquared) {
                return;
            }

            //Update the last update position
            int maxStep = this.triggerRangeSquared/2;
            this.lastUpdateX += Math.min(maxStep,Math.max(-maxStep, x-this.lastUpdateX));
            this.lastUpdateZ += Math.min(maxStep,Math.max(-maxStep, z-this.lastUpdateZ));

            //Compute movement if it happened
            int nx = x>>this.shiftSize;
            int nz = z>>this.shiftSize;

            if (nx == this.currentX && nz == this.currentZ) {
                //No movement
                return;
            }


            //FIXME: not right, needs to only call load/unload on entry and exit, cause atm its acting like a loaded circle

            Long2IntOpenHashMap ops = new Long2IntOpenHashMap();
            while (true) {
                int dir = nz < this.currentZ ? -1 : 1;
                if (nz != this.currentZ) {
                    for (int corner : this.cornerPoints) {
                        int cx = corner >>> 16;
                        int cz = corner & 0xFFFF;

                        ops.addTo(Prel(cx, cz + Math.max(0, dir)), dir);
                        ops.addTo(Prel(cx, -cz + Math.min(0, dir)), -dir);
                        if (cx != 0) {
                            ops.addTo(Prel(-cx, cz + Math.max(0, dir)), dir);
                            ops.addTo(Prel(-cx, -cz + Math.min(0, dir)), -dir);
                        }
                    }

                    this.currentZ += dir;
                }

                dir = nx < this.currentX ? -1 : 1;
                if (nx != this.currentX) {
                    for (int corner : this.cornerPoints) {
                        int cx = corner & 0xFFFF;
                        int cz = corner >>> 16;

                        ops.addTo(Prel(cx + Math.max(0, dir), cz), dir);
                        ops.addTo(Prel(-cx + Math.min(0, dir), cz), -dir);
                        if (cz != 0) {
                            ops.addTo(Prel(cx + Math.max(0, dir), -cz), dir);
                            ops.addTo(Prel(-cx + Math.min(0, dir), -cz), -dir);
                        }
                    }

                    this.currentX += dir;
                }

                //Only break once the coords match
                if (nx == this.currentX && nz == this.currentZ) {
                    break;
                }
            }


            ops.forEach((pos,val)->{
                if (val > 0) {
                    this.enter.callback((int) (long)pos, (int) (pos>>32));
                }
                if (val < 0) {
                    this.exit.callback((int) (long)pos, (int) (pos>>32));
                }
            });
            ops.clear();
        }

        public void fill(int x, int z) {
            this.fill(x, z, null);
        }

        public void fill(int x, int z, Transition2DCallback outsideCallback) {
            int cx = x>>this.shiftSize;
            int cz = z>>this.shiftSize;

            int r2 = this.radius*this.radius;
            for (int a = -this.radius; a <= this.radius; a++) {
            //IntStream.range(-this.radius, this.radius+1).parallel().forEach(a->{
                int b = (int) Math.floor(Math.sqrt(r2-(a*a)));
                for (int c = -b; c <= b; c++) {
                    this.enter.callback(a + cx, c + cz);
                }
                if (outsideCallback != null) {
                    for (int c = -this.radius; c < -b; c++) {
                        outsideCallback.callback(a + cx, c + cz);
                    }

                    for (int c = b+1; c <= this.radius; c++) {
                        outsideCallback.callback(a + cx, c + cz);
                    }
                }
            }//);
        }

        public void setCenter(int x, int z) {
            int cx = x>>this.shiftSize;
            int cz = z>>this.shiftSize;
            this.currentX = cx;
            this.currentZ = cz;
            this.lastUpdateX = x + (((int)(Math.random()*4))<<(this.shiftSize-4));
            this.lastUpdateZ = z + (((int)(Math.random()*4))<<(this.shiftSize-4));
        }
    }
}
/*
        public void update(int x, int z) {
            int MAX_STEPS_PER_UPDATE = 1;


            long dx = this.lastUpdateX - x;
            long dz = this.lastUpdateZ - z;
            long distSquared =  dx*dx + dz*dz;
            if (distSquared < this.triggerRangeSquared) {
                return;
            }

            //TODO: fixme: this last update needs to be incremented by a delta since

            //Update the last update position
            int maxStep = this.triggerRangeSquared/2;
            this.lastUpdateX += Math.min(maxStep,Math.max(-maxStep, x-this.lastUpdateX));
            this.lastUpdateZ += Math.min(maxStep,Math.max(-maxStep, z-this.lastUpdateZ));



            //Compute movement if it happened
            int nx = x>>this.shiftSize;
            int nz = z>>this.shiftSize;

            if (nx == this.currentX && nz == this.currentZ) {
                //No movement
                return;
            }


            //FIXME: not right, needs to only call load/unload on entry and exit, cause atm its acting like a loaded circle

            Long2IntOpenHashMap ops = new Long2IntOpenHashMap();

            int zcount = MAX_STEPS_PER_UPDATE;
            int dir = nz<this.currentZ?-1:1;
            while (nz != this.currentZ) {
                for (int corner : this.cornerPoints) {
                    int cx = corner>>>16;
                    int cz = corner&0xFFFF;

                    ops.addTo(Prel( cx, cz+Math.max(0, dir)), dir);
                    ops.addTo(Prel( cx,-cz+Math.min(0, dir)),-dir);
                    if (cx != 0) {
                        ops.addTo(Prel(-cx, cz+Math.max(0, dir)), dir);
                        ops.addTo(Prel(-cx,-cz+Math.min(0, dir)),-dir);
                    }
                }

                //ops.addTo(Prel(0,  this.radius+Math.max(0, dir)),  dir);
                //ops.addTo(Prel(0, -this.radius+Math.min(0, dir)), -dir);

                this.currentZ += dir;

                if (--zcount == 0) break;
            }

            int xcount = MAX_STEPS_PER_UPDATE;
            dir = nx<this.currentX?-1:1;
            while (nx != this.currentX) {

                for (int corner : this.cornerPoints) {
                    int cx = corner&0xFFFF;
                    int cz = corner>>>16;

                    ops.addTo(Prel( cx+Math.max(0, dir), cz), dir);
                    ops.addTo(Prel(-cx+Math.min(0, dir), cz),-dir);
                    if (cz != 0) {
                        ops.addTo(Prel(cx + Math.max(0, dir), -cz), dir);
                        ops.addTo(Prel(-cx + Math.min(0, dir), -cz), -dir);
                    }
                }

                this.currentX += dir;

                if (--xcount == 0) break;
            }


            ops.forEach((pos,val)->{
                if (val > 0) {
                    this.enter.callback((int) (long)pos, (int) (pos>>32));
                }
                if (val < 0) {
                    this.exit.callback((int) (long)pos, (int) (pos>>32));
                }
            });
            ops.clear();
        }*/
