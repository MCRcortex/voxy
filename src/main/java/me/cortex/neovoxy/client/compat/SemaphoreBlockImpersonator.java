package me.cortex.neovoxy.client.compat;

import me.cortex.neovoxy.common.thread.MultiThreadPrioritySemaphore;

import java.util.concurrent.Semaphore;

public class SemaphoreBlockImpersonator extends Semaphore {
    private final MultiThreadPrioritySemaphore.Block block;
    public SemaphoreBlockImpersonator(MultiThreadPrioritySemaphore.Block block) {
        super(0);
        this.block = block;
    }

    @Override
    public void release(int permits) {
        this.block.release(permits);
    }

    @Override
    public void acquire() throws InterruptedException {
        this.block.acquire();
    }

    @Override
    public boolean tryAcquire() {
        return this.block.tryAcquire();
    }

    @Override
    public int availablePermits() {
        return this.block.availablePermits();
    }
}
