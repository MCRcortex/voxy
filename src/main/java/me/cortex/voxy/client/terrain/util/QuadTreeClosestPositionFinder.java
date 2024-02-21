package me.cortex.voxy.client.terrain.util;

public class QuadTreeClosestPositionFinder {
    public QuadTreeClosestPositionFinder() {

    }

    private static abstract class Node {

    }

    /*
    private static class InnerNode extends Node {
        private final long[] nodes = new long[1+8*8+8*8*8*8];
        private final Node[] children = new Node[8*8*8*8*8*8];
        private long getClosestPoint(long nodePos, int cx, int cz) {
            long node = -1;//this.nodes[];
            if (node == -1) {
                return -1;
            }
            float metric = Float.MAX_VALUE;
            int pos = 0;
            while (node != -1) {
                int id = Long.numberOfTrailingZeros(node);
                node &= ~(1L << id);
                int x = id & 7;
                int y = (id >> 3) & 7;

            }
            return -1;
        }
    }*/


    /*
    private static class PartialNode extends Node {
        private long fullMSK;
        private final Node[] children = new Node[8*8];
        private final int lvl;

        private PartialNode(int lvl) {
            this.lvl = lvl;
        }

        private long getClosestPoint(int[] cacheArray, int cx, int cz) {
            long node = this.fullMSK;
            int minDist = Integer.MAX_VALUE;
            int pointCounter = 0;
            while (node != -1) {
                int id = Long.numberOfTrailingZeros(node);
                node &= ~(1L << id);
                int x = id & 7;
                int z = (id >> 3) & 7;

                int dx = Math.abs(x - (cx >> this.lvl));
                int dz = Math.abs(z - (cz >> this.lvl));
                int dist = Math.max(dx, dz);
                if (dist < minDist) {
                    pointCounter = 0;
                    minDist = dist;
                }
                if (dist == minDist) {
                    cacheArray[pointCounter++] = id;
                }
            }


            return -1;
        }
    }*/
    /*
    private static class InnerNode extends Node {
        private final Node[] nodes = new Node[4];
        private final int lvl;

        private static final int[] ORDERING = new int[]{
                0b00_01_10_11,
                0b01_00_11_10,
                0b10_00_11_01,
                0b11_01_10_00,
        };

        private InnerNode(int lvl) {
            this.lvl = lvl;
        }

        public long getClosestEmpty(long cMin, int cx, int cz) {
            int id = (((cz>>this.lvl)&1)<<1) | ((cx>>this.lvl)&1);
            int ordering = ORDERING[id];
        }
    }*/
}
