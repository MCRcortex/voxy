package me.cortex.voxy.common.world;

import java.util.concurrent.atomic.AtomicInteger;

public final class SectionLoadLimiter {
    private static volatile int maxConcurrent = 2;
    private static final AtomicInteger active = new AtomicInteger();

    private SectionLoadLimiter() {}

    public static void setMaxConcurrent(int max) {
        maxConcurrent = Math.max(1, max);
    }

    public static int getMaxConcurrent() {
        return maxConcurrent;
    }

    public static int getActiveCount() {
        return active.get();
    }

    public static void acquire() {
        while (active.get() >= maxConcurrent) {
            Thread.onSpinWait();
            Thread.yield();
        }
        active.incrementAndGet();
    }

    public static void release() {
        active.decrementAndGet();
    }
}
