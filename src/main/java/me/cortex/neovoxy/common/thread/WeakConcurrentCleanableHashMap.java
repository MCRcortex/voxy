package me.cortex.neovoxy.common.thread;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public class WeakConcurrentCleanableHashMap<K extends LongSupplier, V> {
    //TODO could move to a Cleanable style system possibly?

    private final Consumer<V> valueCleaner;
    private final ReferenceQueue<K> cleanupQueue = new ReferenceQueue<>();

    private final ReentrantLock k2iLock = new ReentrantLock();
    private final Object2LongOpenHashMap<WeakReference<K>> k2i = new Object2LongOpenHashMap<>();
    {
        this.k2i.defaultReturnValue(-1);
    }
    private final Long2ObjectOpenHashMap<V>[] i2v = new Long2ObjectOpenHashMap[1<<4];
    private final ReentrantLock[] i2vLocks = new ReentrantLock[this.i2v.length];
    {
        for (int i = 0; i < this.i2v.length; i++) {
            this.i2v[i] = new Long2ObjectOpenHashMap<>();
            this.i2vLocks[i] = new ReentrantLock();
        }
    }

    private final AtomicInteger count = new AtomicInteger();

    public WeakConcurrentCleanableHashMap(Consumer<V> cleanupConsumer) {
        this.valueCleaner = cleanupConsumer;
    }

    private static int Id2Seg(long id, int MSK) {
        return HashCommon.mix((int)id)&MSK;
    }

    public V computeIfAbsent(K key, Supplier<V> valueOnAbsent) {
        this.cleanup();

        long id = key.getAsLong();
        int bucket = Id2Seg(id, this.i2v.length-1);
        var i2v = this.i2v[bucket];
        var lock = this.i2vLocks[bucket];
        lock.lock();
        if (i2v.containsKey(id)) {
            lock.unlock();
            return i2v.get(id);
        } else {
            var v = valueOnAbsent.get();
            i2v.put(id, v);
            this.k2iLock.lock();
            lock.unlock();
            this.k2i.put(new WeakReference<>(key, this.cleanupQueue), id);
            this.k2iLock.unlock();
            this.count.incrementAndGet();
            return v;
        }
    }

    public void cleanup() {
        WeakReference<K> ref = (WeakReference<K>) this.cleanupQueue.poll();
        if (ref != null) {
            LongArrayFIFOQueue ids = new LongArrayFIFOQueue();
            this.k2iLock.lock();
            do {
                long id = this.k2i.removeLong(ref);
                if (id < 0) continue;
                ids.enqueue(id);
            } while ((ref = (WeakReference<K>) this.cleanupQueue.poll()) != null);
            this.k2iLock.unlock();
            if (ids.isEmpty()) return;
            int count = ids.size();
            while (!ids.isEmpty()) {
                long id = ids.dequeueLong();
                int bucket = Id2Seg(id, this.i2v.length - 1);
                var lock = this.i2vLocks[bucket];
                lock.lock();
                var val = this.i2v[bucket].remove(id);
                lock.unlock();
                if (val != null) {
                    this.valueCleaner.accept(val);
                } else {
                    count--;
                }
            }
            if (this.count.addAndGet(-count)<0) {
                throw new IllegalStateException();
            }
        }
    }

    public List<V> clear() {
        this.cleanup();

        List<V> values = new ArrayList<>(this.size());
        //lock everything
        for (var lock : this.i2vLocks) {
            lock.lock();
        }
        this.k2iLock.lock();
        this.k2i.clear();//Clear here while its safe to do so
        for (var i2v : this.i2v) {
            values.addAll(i2v.values());
            i2v.clear();
        }
        this.count.set(0);
        this.k2iLock.unlock();
        for (var lock : this.i2vLocks) {
            lock.unlock();
        }
        return values;
    }

    public int size() {
        return this.count.get();
    }
}
