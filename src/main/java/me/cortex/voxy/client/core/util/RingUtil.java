package me.cortex.voxy.client.core.util;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

public class RingUtil {
    private static  int computeR(int rd2, int a, int b) {
        return rd2 - (a*a) - (b*b);
    }

    private static  int computeR(int rd2, int a) {
        return rd2 - (a*a);
    }

    private static int pack(int a, int b, int d) {
        int m = ((1<<10)-1);
        return (a&m)|((b&m)<<10)|(d<<20);
    }

    private static int pack(int a, int b) {
        int m = ((1<<16)-1);
        return (a&m)|((b&m)<<16);
    }

    public static int[] generateBoundingHalfSphere(int radius) {
        IntArrayList points = new IntArrayList();
        int rd2 = radius*radius;
        //Generate full sphere points for each axis
        for (int a = - radius; a <= radius; a++) {
            for (int b = - radius; b <= radius; b++) {
                //Basicly do a rearranged form of
                // r^2 = x^2 + y^2 + z^2
                int pd = computeR(rd2, a, b);
                if (pd < 0) {//Cant have -ve space
                    continue;
                }
                pd = (int) Math.sqrt(pd);
                points.add(pack(a,b,pd));
            }
        }
        return points.toIntArray();
    }

    public static int[] generateBoundingHalfCircle(int radius) {
        IntArrayList points = new IntArrayList();
        int rd2 = radius*radius;
        //Generate full sphere points for each axis
        for (int a = - radius; a <= radius; a++) {
            //Basicly do a rearranged form of
            // r^2 = x^2 + y^2
            int pd = computeR(rd2, a);
            if (pd < 0) {//Cant have -ve space
                continue;
            }
            pd = (int) Math.sqrt(pd);
            points.add(pack(a,pd));
        }
        return points.toIntArray();
    }





    public static int[] generatingBoundingCorner2D(int radius) {
        IntOpenHashSet points = new IntOpenHashSet();
        //Do 2 pass (x and y) to generate and cover all points
        for (int i = 0; i <= radius; i++) {
            int other = (int) Math.floor(Math.sqrt(radius*radius - i*i));
            //add points (x,other) and (other,x) as it covers the full spectrum
            points.add((i<<16)|other);
            //points.add((other<<16)|i);
        }

        return points.toIntArray();
    }
}

