package me.cortex.voxy.common.util;

import java.lang.ref.Cleaner;

public abstract class TrackedObject {
    //TODO: maybe make this false? for performance overhead?
    public static final boolean TRACK_OBJECT_ALLOCATIONS = System.getProperty("voxy.ensureTrackedObjectsAreFreed", "true").equals("true");
    public static final boolean TRACK_OBJECT_ALLOCATION_STACKS = System.getProperty("voxy.trackObjectAllocationStacks", "false").equals("true");

    private final Ref ref;
    protected TrackedObject() {
        this(true);
    }

    protected TrackedObject(boolean shouldTrack) {
        this.ref = register(shouldTrack, this);
    }

    protected void free0() {
        if (this.isFreed()) {
            throw new IllegalStateException("Object " + this + " was double freed.");
        }
        this.ref.freedRef[0] = true;
        if (TRACK_OBJECT_ALLOCATIONS) {
            this.ref.cleanable.clean();
        }
    }

    public abstract void free();

    public void assertNotFreed() {
        if (isFreed()) {
            throw new IllegalStateException("Object " + this + " should not be free, but is");
        }
    }

    public boolean isFreed() {
        return this.ref.freedRef[0];
    }

    public record Ref(Cleaner.Cleanable cleanable, boolean[] freedRef) {}

    private static final Cleaner cleaner;
    static {
        if (TRACK_OBJECT_ALLOCATIONS) {
            cleaner = Cleaner.create();
        } else {
            cleaner = null;
        }
    }
    public static Ref register(boolean track, Object obj) {
        boolean[] freed = new boolean[1];
        Cleaner.Cleanable cleanable = null;
        if (TRACK_OBJECT_ALLOCATIONS && track) {
            String clazz = obj.getClass().getName();
            Throwable trace;
            if (TRACK_OBJECT_ALLOCATION_STACKS) {
                trace = new Throwable();
                trace.fillInStackTrace();
            } else {
                trace = null;
            }
            cleanable = cleaner.register(obj, () -> {
                if (!freed[0]) {
                    System.err.println("Object named: " + clazz + " was not freed, location at:\n");
                    if (trace != null) {
                        trace.printStackTrace();
                    } else {
                        System.err.println("Enable allocation stack tracing");
                    }
                    System.err.flush();
                }
            });
        }
        return new Ref(cleanable, freed);
    }
}
