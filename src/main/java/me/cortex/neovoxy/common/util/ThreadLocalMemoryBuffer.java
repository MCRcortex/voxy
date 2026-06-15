package me.cortex.neovoxy.common.util;

import static me.cortex.neovoxy.common.util.GlobalCleaner.CLEANER;

public class ThreadLocalMemoryBuffer {
    private static MemoryBuffer createMemoryBuffer(long size) {
        var buffer = new MemoryBuffer(size);
        var ref = MemoryBuffer.createUntrackedUnfreeableRawFrom(buffer.address, buffer.size);
        CLEANER.register(ref, buffer::free);
        return ref;
    }

    //TODO: make this much better
    private final ThreadLocal<MemoryBuffer> threadLocal;

    public ThreadLocalMemoryBuffer(long size) {
        this.threadLocal = ThreadLocal.withInitial(()->createMemoryBuffer(size));
    }

    public static MemoryBuffer create(long size) {
        return createMemoryBuffer(size);
    }

    public MemoryBuffer get() {
        return this.threadLocal.get();
    }
}
