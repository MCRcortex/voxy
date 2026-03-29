package me.cortex.voxy.common;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import me.cortex.voxy.common.world.WorldEngine;

import static me.cortex.voxy.common.world.WorldEngine.UPDATE_TYPE_CHILD_EXISTENCE_BIT;

public class DebugUtils {
    public static void verifyAllTopLevelNodes(WorldEngine engine, boolean attemptRepair) {
        engine.markActive();
        var worker = new Thread(()->{
            engine.acquireRef();
            try {
                Logger.info("Verifying top level node masks, start");
                Logger.showInHUD("Starting tln child verification" + (attemptRepair?" attemptting repairs on error":""));
                LongArrayFIFOQueue positions = new LongArrayFIFOQueue();
                engine.storage.iteratePositions(WorldEngine.MAX_LOD_LAYER, positions::enqueue);
                int count = positions.size();
                Logger.info("Verifying " + count + " top level nodes");
                while (!positions.isEmpty()) {
                    if (engine.instanceIn != null && !engine.instanceIn.isRunning()) break;
                    long pos = positions.dequeueLong();
                    verifyTopNodeChildren(engine, WorldEngine.getX(pos), WorldEngine.getY(pos), WorldEngine.getZ(pos), attemptRepair);
                    //if ((count - positions.size())/count)
                }
                if (engine.instanceIn != null && !engine.instanceIn.isRunning()) {
                    Logger.info("Verification aborted due to shutdown");
                } else {
                    Logger.info("Verification complete");
                    Logger.showInHUD("Verification complete");
                }
            } finally {
                engine.releaseRef();
            }
        });
        worker.setDaemon(true);
        worker.setName("Verification thread");
        worker.start();
    }


    public static void verifyTopNodeChildren(WorldEngine world, int X, int Y, int Z, boolean tryRepair) {
        //TODO: can speed this up if needed by not getting the children and instead caching the previous getNonEmptyChildren result
        boolean loggedTLNPos = false;
        for (int lvl = 0; lvl < 5; lvl++) {
            for (int y = (Y<<4)>>lvl; y < ((Y+1)<<4)>>lvl; y++) {
                for (int x = (X<<4)>>lvl; x < ((X+1)<<4)>>lvl; x++) {
                    for (int z = (Z<<4)>>lvl; z < ((Z+1)<<4)>>lvl; z++) {
                        if (world.instanceIn != null && !world.instanceIn.isRunning()) {
                            return;
                        }
                        if (lvl == 0) {
                            var own = world.acquireIfExists(lvl, x, y, z);
                            if (own != null) {
                                if ((own.getNonEmptyChildren() != 0) ^ (own.getNonEmptyBlockCount() != 0)) {
                                    if (!loggedTLNPos) {
                                        Logger.error("Error verifying top level node: " + X + "," + Y + "," + Z);
                                        loggedTLNPos = true;
                                    }
                                    Logger.error("Lvl 0 node not marked correctly " + WorldEngine.pprintPos(own.key) + " expected: " + (own.getNonEmptyBlockCount() != 0) + " got " + (own.getNonEmptyChildren() != 0));
                                    if (tryRepair) {
                                        own.updateLvl0State();
                                        world.markDirty(own, UPDATE_TYPE_CHILD_EXISTENCE_BIT, 0);
                                    }
                                }
                                own.release();
                            }
                        } else {
                            byte msk = 0;
                            for (int child = 0; child < 8; child++) {
                                var section = world.acquireIfExists(lvl-1, (child&1)+(x<<1), ((child>>2)&1)+(y<<1), ((child>>1)&1)+(z<<1));
                                if (section != null) {
                                    msk |= (byte) (section.getNonEmptyChildren() != 0 ? (1 << child) : 0);
                                    section.release();
                                }
                            }
                            var own = world.acquireIfExists(lvl, x, y, z);
                            if (own != null) {
                                if (own.getNonEmptyChildren() != msk) {
                                    if (!loggedTLNPos) {
                                        Logger.error("Error verifying top level node: " + X + "," + Y + "," + Z);
                                        loggedTLNPos = true;
                                    }
                                    Logger.error("Section empty child mask not correct " + WorldEngine.pprintPos(own.key) + " got: " + String.format("%8s", Integer.toBinaryString(Byte.toUnsignedInt(own.getNonEmptyChildren()))).replace(' ', '0') + " expected: " + String.format("%8s", Integer.toBinaryString(Byte.toUnsignedInt(msk))).replace(' ', '0'));
                                    if (tryRepair) {
                                        for (int child = 0; child < 8; child++) {
                                            var section = world.acquireIfExists(lvl-1, (child&1)+(x<<1), ((child>>2)&1)+(y<<1), ((child>>1)&1)+(z<<1));
                                            if (section != null) {
                                                own.updateEmptyChildState(section);
                                                section.release();
                                            }
                                        }
                                        world.markDirty(own, UPDATE_TYPE_CHILD_EXISTENCE_BIT, 0);
                                    }
                                }
                                own.release();
                            } else if (msk != 0) {
                                if (!loggedTLNPos) {
                                    Logger.error("Error verifying top level node: " + X + "," + Y + "," + Z);
                                    loggedTLNPos = true;
                                }
                                Logger.error("Section doesnt exist in db but has non empty children " + WorldEngine.pprintPos(WorldEngine.getWorldSectionId(lvl, x, y, z)) + " has children: " + String.format("%8s", Integer.toBinaryString(Byte.toUnsignedInt(msk))).replace(' ', '0'));
                            }
                        }
                    }
                }
            }
        }
    }
}
