package me.cortex.voxy.common.thread;

import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.Pair;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class Service {
    private final PerThreadContextExecutor executor;
    private final ServiceManager sm;
    volatile long weight;
    final String name;
    final BooleanSupplier limiter;

    private final Semaphore tasks = new Semaphore(0);
    private final AtomicInteger activeJobs = new AtomicInteger();
    private volatile int maxConcurrent = Integer.MAX_VALUE;
    private volatile boolean isLive = true;
    private volatile boolean isStopping = false;

    Service(Supplier<Pair<Runnable, Runnable>> ctxSupplier, ServiceManager sm, long weight, String name, BooleanSupplier limiter) {
        this.sm = sm;
        this.weight = weight;
        this.name = name;
        this.limiter = limiter;

        this.executor = new PerThreadContextExecutor(ctxSupplier, e->sm.handleException(this, e));
    }

    public void setMaxConcurrent(int max) {
        this.maxConcurrent = Math.max(1, max);
    }

    public void setWeight(long weight) {
        this.weight = Math.max(1, weight);
    }

    public int getActiveJobs() {
        return this.activeJobs.get();
    }

    public int getMaxConcurrent() {
        return this.maxConcurrent;
    }

    boolean canAcceptJobs() {
        if (this.activeJobs.get() >= this.maxConcurrent) {
            return false;
        }
        return this.limiter == null || this.limiter.getAsBoolean();
    }

    public void execute() {
        if (this.isStopping) {
            Logger.error("Tried executing on a dead service");
            return;
        }
        this.tasks.release();
        this.sm.execute(this);
    }

    boolean runJob() {
        if (this.isStopping||!this.isLive) {
            return false;
        }
        if (!this.tasks.tryAcquire()) {
            //Failed to get the job, probably due to a race condition
            return false;
        }
        if (!this.canAcceptJobs()) {
            this.tasks.release();
            return false;
        }
        this.activeJobs.incrementAndGet();
        try {
            if (!this.executor.run()) {//Run the job
                throw new IllegalStateException("Executor failed to run");
            }
        } finally {
            this.activeJobs.decrementAndGet();
        }
        return true;
    }

    public boolean isLive() {
        return this.isLive&&!this.isStopping;
    }

    public int numJobs() {
        return this.tasks.availablePermits();
    }

    public void blockTillEmpty() {
        while (this.isLive() && this.numJobs() != 0) {
            Thread.yield();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public int shutdown() {
        if (this.isStopping) {
            throw new IllegalStateException("Service not live");
        }
        this.isStopping = true;//First mark the service as stopping
        this.sm.removeService(this);//Remove the service this is so that new jobs are never executed
        this.executor.shutdown();//Await shutdown of all running jobs
        int remaining = this.tasks.drainPermits();//Drain the remaining tasks to 0
        this.isLive = false;//Mark the service as dead
        this.sm.remJobs(remaining);
        return remaining;
    }

    public boolean steal() {
        if (!this.tasks.tryAcquire()) {
            return false;
        }
        this.sm.remJobs(1);
        return true;
    }

    public int drain() {
        int tasks = this.tasks.drainPermits();
        if (tasks != 0) {
            this.sm.remJobs(tasks);
        }
        return tasks;
    }
}
