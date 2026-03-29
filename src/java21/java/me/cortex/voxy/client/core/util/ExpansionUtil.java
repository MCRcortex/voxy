package me.cortex.voxy.client.core.util;

public class ExpansionUtil {

    public static int expand(int i, int mask) {
        return Integer.expand(i, mask);
    }

    public static int compress(int i, int mask) {
        return Integer.compress(i, mask);
    }

    public static long expand(long i, long mask) {
        return Long.expand(i, mask);
    }

    public static long compress(long i, long mask) {
        return Long.compress(i, mask);
    }

    public static boolean isJava21() {
        return true;
    }
}