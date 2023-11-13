package me.cortex.voxelmon.core;

//Contains the logic to determine what is loaded and at what LoD level, dispatches render changes
// also determines what faces are built etc

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.cortex.voxelmon.core.rendering.AbstractFarWorldRenderer;
import me.cortex.voxelmon.core.rendering.RenderTracker;
import me.cortex.voxelmon.core.rendering.building.RenderGenerationService;
import me.cortex.voxelmon.core.util.DebugUtil;
import me.cortex.voxelmon.core.util.RingUtil;
import me.cortex.voxelmon.core.world.WorldEngine;

//Can use ring logic
// i.e. when a player moves the rings of each lod change (how it was doing in the original attempt)
// also have it do directional quad culling and rebuild the chunk if needed (this shouldent happen very often) (the reason is to significantly reduce draw calls)
// make the rebuild range like +-5 chunks along each axis (that means at higher levels, should only need to rebuild like)
// 4 sections or something
public class DistanceTracker {
    private final TransitionRing2D[] rings;
    private final RenderTracker tracker;
    public DistanceTracker(RenderTracker tracker, int rings) {
        this.rings = new TransitionRing2D[rings+1];
        this.tracker = tracker;

        int DIST = 16;

        this.rings[0] = new TransitionRing2D(5, DIST, (x,z)->{
            if (true) return;
            for (int y = -2; y < 10; y++) {
                this.tracker.remLvl0(x, y, z);
            }
        }, (x, z) -> {
            for (int y = -2; y < 10; y++) {
                this.tracker.addLvl0(x, y, z);
            }
        });

        for (int i = 1; i < rings; i++) {
            int capRing = i;
            this.rings[i] = new TransitionRing2D(5+i, DIST, (x, z) -> this.dec(capRing, x, z), (x, z) -> this.inc(capRing, x, z));
        }
    }

    private void inc(int lvl, int x, int z) {
        for (int y = -2>>lvl; y < 10>>lvl; y++) {
            this.tracker.inc(lvl, x, y, z);
        }
    }

    private void dec(int lvl, int x, int z) {
        for (int y = -2>>lvl; y < 10>>lvl; y++) {
            this.tracker.dec(lvl, x, y, z);
        }
    }

    //How it works is there are N ring zones (one zone for each lod boundary)
    // the transition zone is what determines what lods are rendered etc (and it biases higher lod levels cause its easier)
    // the transition zone is only ever checked when the player moves 1<<(4+lodlvl) blocks, its position is set

    //if the center suddenly changes (say more than 1<<(7+lodlvl) block) then invalidate the entire ring and recompute
    // the lod sections
    public void setCenter(int x, int y, int z) {
        for (var ring : rings) {
            if (ring != null) {
                ring.update(x, z);
            }
        }
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
            int dx = this.lastUpdateX - x;
            int dz = this.lastUpdateZ - z;
            int distSquared =  dx*dx + dz*dz;
            if (distSquared < this.triggerRangeSquared) {
                return;
            }
            //Update the last update position
            this.lastUpdateX = x;
            this.lastUpdateZ = z;



            //Compute movement if it happened
            int nx = x>>this.shiftSize;
            int nz = z>>this.shiftSize;

            if (nx == this.currentX && nz == this.currentZ) {
                //No movement
                return;
            }


            //FIXME: not right, needs to only call load/unload on entry and exit, cause atm its acting like a loaded circle

            Long2IntOpenHashMap ops = new Long2IntOpenHashMap();


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
            }

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
    }
}
