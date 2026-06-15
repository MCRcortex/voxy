package me.cortex.neovoxy.common.util;

import java.lang.invoke.VarHandle;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class MessageQueue <T> {
    private final Consumer<T> consumer;
    private final ConcurrentLinkedDeque<T> queue = new ConcurrentLinkedDeque<>();
    private final AtomicInteger count = new AtomicInteger(0);

    public MessageQueue(Consumer<T> consumer) {
        this.consumer = consumer;
    }

    public void push(T obj) {
        this.queue.add(obj);
        this.count.addAndGet(1);
    }

    public int consume() {
        return this.consume(Integer.MAX_VALUE);
    }

    public int consume(int max) {
        if (this.count.get() == 0) {
            return 0;
        }
        int i = 0;
        while (i < max) {
            var entry = this.queue.poll();
            if (entry == null) break;
            i++;
            this.consumer.accept(entry);
        }
        if (i != 0) {
            this.count.addAndGet(-i);
        }
        return i;
    }

    public int consumeNano(long budget) {
        //if (budget < 25_000) return 0;
        if (this.count.get() == 0) {
            return 0;
        }
        int i = 0;
        long nano = System.nanoTime();
        VarHandle.fullFence();
        do {
            var entry = this.queue.poll();
            if (entry == null) break;
            i++;
            this.consumer.accept(entry);
        } while ((System.nanoTime()-nano) < budget);
        if (i != 0) {
            this.count.addAndGet(-i);
        }
        return i;
    }

    public final void clear(Consumer<T> cleaner) {
        do {
            var v = this.queue.poll();
            if (v == null) {
                break;
            }
            cleaner.accept(v);
        } while (true);
    }

    public int count() {
        return this.count.get();
    }
}
