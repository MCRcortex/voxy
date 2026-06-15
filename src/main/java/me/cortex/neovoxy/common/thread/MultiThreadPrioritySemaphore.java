package me.cortex.neovoxy.common.thread;

import me.cortex.neovoxy.common.util.TrackedObject;

import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

//Basiclly acts as a priority based mutlti semaphore
// allows the pooling of multiple threadpools together while prioritizing the work the original was ment for
public class MultiThreadPrioritySemaphore {
    public static final class Block extends TrackedObject {
        private final Semaphore blockSemaphore = new Semaphore(0);//The work pool semaphore
        private final Semaphore localSemaphore = new Semaphore(0);//The local semaphore
        //private final AtomicInteger debt = new AtomicInteger();//the debt of the work pool semphore with respect to the usage
        private final MultiThreadPrioritySemaphore man;

        Block(MultiThreadPrioritySemaphore man) {
            this.man = man;
        }

        public void release(int permits) {
            //release local then block to prevent race conditions
            this.localSemaphore.release(permits);
            this.blockSemaphore.release(permits);
        }

        public void acquire() {
            this.acquire(true);
        }
        public void acquire(boolean runJob) {//Block until a permit for this block is availbe, other jobs maybe executed while we wait
            /*
            while (true) {
                this.blockSemaphore.acquireUninterruptibly();//Block on all
                if (this.localSemaphore.tryAcquire()) {//We prioritize locals first
                    return;
                }
                if (runJob) {
                    //It wasnt a local job so run
                    this.man.tryRun(this);
                } else {
                    this.blockSemaphore.release(1);
                    Thread.onSpinWait();
                    Thread.yield();
                }
            }*/

            //Absolutly no idea if this shitty thing functions correctly... at all, it very much probably doesnt
            while (true) {
                if (runJob) {
                    this.blockSemaphore.acquireUninterruptibly();//Block on all
                    if (this.localSemaphore.tryAcquire()) {//We prioritize locals first
                        return;
                    }
                    if (this.man.tryRun(this)) {//Returns true if it captured a local job
                        break;
                    }
                } else {
                    this.localSemaphore.acquireUninterruptibly();
                    if (!this.blockSemaphore.tryAcquire()) {
                        //This is technicanlly/actually a failure state cause blockSemaphore could have more
                    }
                    break;
                }
            }
        }


        public void free() {
            this.man.freeBlock(this);
            this.free0();
        }

        public int availablePermits() {
            return this.localSemaphore.availablePermits();
        }

        public boolean tryAcquire() {
            if (this.localSemaphore.availablePermits()==0) return false;//Quick exit
            if (!this.blockSemaphore.tryAcquire()) return false;//There is definatly none
            if (this.localSemaphore.tryAcquire()) {
                //we acquired a proper permit
                return true;
            } else {
                //We must release the other permit as we dont do processing here
                this.blockSemaphore.release(1);
                return false;
            }
        }
    }

    private final Semaphore pooledSemaphore = new Semaphore(0);
    private final IntSupplier executor;

    private volatile Block[] blocks = new Block[0];

    public MultiThreadPrioritySemaphore(IntSupplier executor) {
        this.executor = executor;
    }

    public synchronized Block createBlock() {
        var block = new Block(this);
        var blocks = Arrays.copyOf(this.blocks, this.blocks.length+1);
        blocks[blocks.length-1] = block;
        this.blocks = blocks;
        return block;
    }

    private synchronized void freeBlock(Block block) {
        var ob = this.blocks;
        var blocks = new Block[ob.length-1];
        int j = 0;
        for (int i = 0; i <= blocks.length; i++) {
            if (ob[i] != block) {
                blocks[j++] = ob[i];
            }
        }
        if (j != blocks.length) {
            throw new IllegalStateException("Could not find the service in the services array");
        }
        this.blocks = blocks;
    }

    public void pooledRelease(int permits) {
        this.pooledSemaphore.release(permits);
        for (var block : this.blocks) {
            block.blockSemaphore.release(permits);
        }
    }

    private boolean tryRun(Block block) {
        if (!this.pooledSemaphore.tryAcquire()) {//No jobs for the unified pool
            return false;
        }
        /*
        for (var otherBlock : this.blocks) {
            if (otherBlock != block) {
                block.debt.incrementAndGet();
            }
        }*/
        //Run the pooled job
        while (true) {
            int status = this.executor.getAsInt();
            if (status == 0) return false;//We finished pure and true
            if (status == 1) return false;// we didnt run a job because there either wasnt any or no services exist
            if (2 <= status) {//2 and 3 mean failed to find a service that can currently run, but should try again after a delay
                try {
                    if (block.localSemaphore.tryAcquire(10, TimeUnit.MILLISECONDS)) {//Await 10 millis for a local job to come in
                        //We do this confusing thing
                        block.blockSemaphore.tryAcquire();//Try acquire the block that we just got
                        this.pooledRelease(1);//We need to release back into the pool
                        return true;
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
