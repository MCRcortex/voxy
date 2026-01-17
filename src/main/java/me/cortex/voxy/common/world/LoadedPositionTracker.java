package me.cortex.voxy.common.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class LoadedPositionTracker {
    private final Supplier<Object> factory;
    private static final Object SENTINEL_LOCK = new Object();

    public LoadedPositionTracker(Supplier<Object> factory) {
        this.factory = factory;
        this.setSize(12);
    }

    private void setSize(int bits) {
        this.mask = (1<<bits)-1;
        this.key = new long[1<<bits];
        this.value = new Object[1<<bits];
        this.zeroObj = null;
    }

    public Object getSecOrMakeLoader(long loc) {
        loc = mix(loc);

        //TODO: upsize if needed

        if (loc == 0) {//Special case, lock free
            var var = this.zeroObj;
            if (var == null) {
                var replace = this.factory.get();
                do {
                    var = ZERO_OBJ_HANDLE.compareAndExchange(this, null, replace);
                } while (var == SENTINEL_LOCK);

                if (var == null) {
                    var = replace;
                }
            }
            return var;
        } else {
            this.aReadLock();
            int pos = this.findAcquire(loc);
            if (pos < 0) {
                pos = -pos-1;
                //No entry found but we have acquired a write location
                // this should be _unique_ to us as in we are the only thread with it
                // which means we also dont need any atomic operations on it
                if (this.value[pos] == null) {
                    throw new IllegalStateException();
                }
                Object val = this.value[pos] = this.factory.get();
                VarHandle.releaseFence();
                this.rReadLock();
                return val;
            } else {
                Object val;
                while ((val=this.value[pos])==null || val == SENTINEL_LOCK) {
                    Thread.onSpinWait();//Wait to acquire
                    VarHandle.acquireFence();
                }
                this.rReadLock();//Cant move before the release acquire sadly as cant have it do a shuffle
                return val;
            }
        }
    }

    //Does an atomic exchange with the value at the given location
    public Object exchange(long loc, Object with) {
        if (with == null) throw new IllegalArgumentException("with cannot be null");
        loc = mix(loc);
        if (loc == 0) {//Special case
            Object val;
            while (true) {
                val = ZERO_OBJ_HANDLE.get(this);
                if (val == SENTINEL_LOCK) continue;
                if (ZERO_OBJ_HANDLE.compareAndSet(this, val, with)) break;
            }
            if (val == null) throw new IllegalStateException();
            return val;
        } else {
            this.aReadLock();
            int pos = this.find(loc);
            this.rReadLock();
            if (pos < 0) {
                throw new IllegalStateException("Position not found");
            } else {
                var val = this.value[pos];
                this.value[pos] = with;
                this.rReadLock();
                if (val == null) {
                    throw new IllegalStateException("Value was null");
                }
                return val;
            }
        }
    }

    public Object removeIfCondition(long loc, Predicate<Object> test) {
        loc = mix(loc);
        if (loc == 0) {//Special case
            var val = this.value;
            if (test.test(val)) {
                return ZERO_OBJ_HANDLE.compareAndExchange(this, val, null);
            }
            return null;
        } else {
            this.aReadLock();//Write lock as we need to ensure correctness
            int pos = this.find(loc);
            this.rReadLock();
            if (pos < 0) {
                return null;//did not remove it as it does not exist
            } else {
                this.aWriteLock();
                var val = this.value[pos];
                if (test.test(val)) {
                    //Remove pos
                    this.value[pos] = null;
                    this.shiftKeys(pos);
                    this.rWriteLock();
                    return val;
                } else {
                    //Test failed, dont remove
                    this.rWriteLock();
                    return null;
                }
            }
        }
    }



    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    private void aReadLock() {//Acquire read lock, blocking

    }

    private void rReadLock() {//Release read lock
    }

    private void aWriteLock() {//Acquire write lock, blocking

    }

    private void rWriteLock() {//Release write lock

    }



    //Faster impl of Long2ObjectOpenHashMap that allows for custom atomic operations on the value fields
    private transient long[] key;
    private transient Object[] value;
    private transient int mask;

    private transient Object zeroObj;



    //k is the already hashed and mixed entry
    private int find(long k) {
        final long[] key = this.key;
        final int msk = this.mask;
        long curr;
        int pos;
        // The starting point.
        if ((curr = key[pos = (int)k&msk]) == 0) return -(pos + 1);
        if (k == curr) return pos;
        // There's always an unused entry.
        while (true) {
            if ((curr = key[pos = (pos + 1) & msk]) == 0) return -(pos + 1);
            if (k == curr) return pos;
        }
    }

    private int findAcquire(long k) {//Finds key or atomically acquires the position of where to insert one
        final long[] key = this.key;
        final int msk = this.mask;
        int pos = (int)k&msk;
        long curr;
        // The starting point.
        if ((curr = key[pos]) == 0) {
            //Try to atomically acquire the position
            curr = (long) LONG_ARR_HANDLE.compareAndExchange(key, pos, 0, k);//The witness becomes the curr
            if (curr == 0) {//We got the lock
                return -(pos+1);
            }
        }
        if (k == curr) return pos;
        // There's always an unused entry.
        while (true) {
            if ((curr = key[pos = (pos + 1) & msk]) == 0) {
                //Try to atomically acquire the position
                curr = (long) LONG_ARR_HANDLE.compareAndExchange(key, pos, 0, k);
                if (curr == 0) {//We got the lock
                    return -(pos+1);
                }
            }
            if (k == curr) return pos;
        }
    }

    private void shiftKeys(int pos) {
        // Shift entries with the same hash.
        // This is a standard open-addressing deletion with linear probing.
        int last;
        long curr;
        final long[] key = this.key;
        final Object[] value = this.value;
        final int msk = this.mask;

        while (true) {
            last = pos;
            pos = (pos + 1) & msk;

            while (true) {
                curr = key[pos];
                if (curr == 0) {
                    key[last] = 0;
                    value[last] = null;
                    return;
                }

                int slot = (int) curr & msk;

                // Branchless check: does 'slot' lie outside the range (last, pos] cyclically?
                // This is equivalent to: slot should be moved to 'last'
                // The condition is true when slot is NOT in the range (last, pos] (cyclically)
                // Using: ((slot - last - 1) & msk) >= ((pos - last) & msk) but avoiding branches
                int slotDist = (slot - last - 1) & msk;
                int posDist = (pos - last) & msk;

                // If slotDist >= posDist, we should break and move this entry
                if (slotDist >= posDist) break;

                pos = (pos + 1) & msk;
            }
            key[last] = curr;
            value[last] = value[pos];
        }
    }

    private static final long LONG_PHI = 0x9E3779B97F4A7C15L;

    /*
    public static long mix(long seed) {
        seed ^= LONG_PHI;
        seed = (seed ^ seed >>> 30) * -4658895280553007687L;
        seed = (seed ^ seed >>> 27) * -7723592293110705685L;
        return seed ^ seed >>> 31;
    }*/

    /*
    public static long mix(final long x) {
        long h = x ^ LONG_PHI;
        h *= LONG_PHI;
        h ^= h >>> 32;
        return h ^ (h >>> 16);
    }*/
    public static long mix(final long x) {
        long h = x * LONG_PHI;
        h ^= h >>> 32;
        return h ^ (h >>> 16);
    }


    //Utils
    private static final VarHandle LONG_ARR_HANDLE = MethodHandles.arrayElementVarHandle(long[].class);
    private static final VarHandle ZERO_OBJ_HANDLE;

    static {
        try {
            ZERO_OBJ_HANDLE = MethodHandles.lookup().findVarHandle(LoadedPositionTracker.class, "zeroObj", Object.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }





    //Test

    public static void main(String[] args) throws InterruptedException, IOException {
        var map = new LoadedPositionTracker(AtomicInteger::new);
        var t = new Thread[20];
        Long2ObjectOpenHashMap<Object> a = new Long2ObjectOpenHashMap<>(3000);
        ReentrantLock l = new ReentrantLock();
        for (int j = 0; j<t.length; j++) {
            int finalJ = j;
            t[j] = new Thread(()->{
                var r = new Random((finalJ +1)*1482791);
                for (int i = 0; i < 20024000; i++) {
                    long p = r.nextInt(3000)*5981754211L;
                    if (false) {
                        l.lock();
                        var val = a.computeIfAbsent(p, lll->new AtomicInteger());
                        l.unlock();
                        if (val instanceof AtomicInteger ai) {
                            if (ai.incrementAndGet() > 2) {
                                l.lock();
                                a.put(p, new AtomicInteger[]{ai});
                                l.unlock();
                            }
                        } else if (val instanceof AtomicInteger[] aia) {
                            var ai = aia[0];
                            ai.incrementAndGet();
                        }
                    }
                    if (true) {
                        Object entry = map.getSecOrMakeLoader(p);
                        if (entry instanceof AtomicInteger ai) {
                            if (ai.incrementAndGet() > 2) {
                                map.exchange(p, new AtomicInteger[]{ai});
                            }
                        } else if (entry instanceof AtomicInteger[] aia) {
                            var ai = aia[0];
                            ai.incrementAndGet();
                        } else {
                            throw new IllegalStateException();
                        }
                        if (false&&r.nextBoolean()) {
                            map.removeIfCondition(p, obj -> {
                                if (obj instanceof AtomicInteger ai) {
                                    if (ai.get() > 100) {
                                        return true;
                                    }
                                    return false;
                                } else if (obj instanceof AtomicInteger[] aia) {
                                    var ai = aia[0];
                                    if (ai.get() > 200) {
                                        return true;
                                    }
                                    return false;
                                } else {
                                    throw new IllegalStateException();
                                }
                            });
                        }
                    }
                }
            });
            t[j].start();
        }
        long start = System.currentTimeMillis();
        for (var tt : t) {
            tt.join();
        }
        System.err.println(System.currentTimeMillis()-start);
        for (var entries : a.long2ObjectEntrySet()) {
            var val = map.getSecOrMakeLoader(entries.getLongKey());
            int iv = 0;
            if (val instanceof AtomicInteger ai) {
                iv = ai.get();
            } else {
                iv = ((AtomicInteger[])val)[0].get();
            }

            val = entries.getValue();
            int iv2 = 0;
            if (val instanceof AtomicInteger ai) {
                iv2 = ai.get();
            } else {
                iv2 = ((AtomicInteger[])val)[0].get();
            }
            if (iv != iv2) {
                throw new IllegalStateException();
            }
        }
    }
}
