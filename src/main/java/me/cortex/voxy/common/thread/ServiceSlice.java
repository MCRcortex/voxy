package me.cortex.voxy.common.thread;

import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.Pair;
import me.cortex.voxy.common.util.TrackedObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class ServiceSlice extends TrackedObject {
    final String name;
    final int weightPerJob;
    volatile boolean alive = true;
    private final ServiceThreadPool threadPool;
    private Supplier<Pair<Runnable, Runnable>> workerGenerator;
    final Semaphore jobCount = new Semaphore(0);
    private final Runnable[] runningCtxs;
    private final Runnable[] cleanupCtxs;
    private final AtomicInteger activeCount = new AtomicInteger();
    private final AtomicInteger jobCount2 = new AtomicInteger();
    private final BooleanSupplier condition;

    ServiceSlice(ServiceThreadPool threadPool, Supplier<Pair<Runnable, Runnable>> workerGenerator, String name, int weightPerJob, BooleanSupplier condition) {
        this.threadPool = threadPool;
        this.condition = condition;
        this.runningCtxs = new Runnable[threadPool.getThreadCount()];
        this.cleanupCtxs = new Runnable[threadPool.getThreadCount()];
        this.name = name;
        this.weightPerJob = weightPerJob;
        this.setWorkerGenerator(workerGenerator);
    }

    protected void setWorkerGenerator(Supplier<Pair<Runnable, Runnable>> workerGenerator) {
        this.workerGenerator = workerGenerator;
    }

    boolean doRun(int threadIndex) {
        //If executable
        if (!this.condition.getAsBoolean()) {
            return false;
        }

        //Run this thread once if possible
        if (!this.jobCount.tryAcquire()) {
            return false;
        }

        if (!this.alive) {
            return true;//Return true because we have "consumed" the job (needed to keep weight tracking correct)
        }

        this.activeCount.incrementAndGet();

        //Check that we are still alive
        if (!this.alive) {
            if (this.activeCount.decrementAndGet() < 0) {
                throw new IllegalStateException("Alive count negative!");
            }
            return true;
        }

        //If the running context is null, create and set it
        var ctx = this.runningCtxs[threadIndex];
        if (ctx == null) {
            var pair = this.workerGenerator.get();
            ctx = pair.left();
            this.cleanupCtxs[threadIndex] = pair.right();//Set cleanup
            this.runningCtxs[threadIndex] = ctx;
        }

        //Run the job
        try {
            ctx.run();
        } catch (Exception e) {
            Logger.error("Unexpected error occurred while executing a service job, expect things to break badly", e);
            MinecraftClient.getInstance().execute(()->MinecraftClient.getInstance().player.sendMessage(Text.literal("A voxy service had an exception while executing please check logs and report error"), true));
        } finally {
            if (this.activeCount.decrementAndGet() < 0) {
                throw new IllegalStateException("Alive count negative!");
            }
            if (this.jobCount2.decrementAndGet() < 0) {
                throw new IllegalStateException("Job count negative!");
            }
        }
        return true;
    }

    //Tells the system that a single instance of this service needs executing
    public void execute() {
        if (!this.alive) {
            Logger.error("Tried to do work on a dead service: " + this.name, new Throwable());
            return;
        }
        this.jobCount.release();
        this.jobCount2.incrementAndGet();
        this.threadPool.execute(this);
    }

    public void shutdown() {
        this.alive = false;

        //Wait till all is finished
        while (this.activeCount.get() != 0) {
            Thread.onSpinWait();
        }

        //Tell parent to remove
        this.threadPool.removeService(this);

        this.runCleanup();

        super.free0();
    }

    private void runCleanup() {
        for (var runnable : this.cleanupCtxs) {
            if (runnable != null) {
                runnable.run();
            }
        }
        Arrays.fill(this.cleanupCtxs, null);
    }

    @Override
    public void free() {
        this.shutdown();
    }

    public int getJobCount() {
        return this.jobCount.availablePermits();
    }

    public boolean hasJobs() {
        return this.jobCount.availablePermits() != 0;
    }

    boolean workConditionMet() {
        return this.condition.getAsBoolean();
    }

    public void blockTillEmpty() {
        while (this.activeCount.get() != 0 && this.alive) {
            while (this.jobCount2.get() != 0 && this.alive) {
                Thread.onSpinWait();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            Thread.yield();
        }
    }

    //Steal a job, if there is no job available return false
    public boolean steal() {
        if (!this.jobCount.tryAcquire()) {
            return false;
        }
        if (this.jobCount2.decrementAndGet() < 0) {
            throw new IllegalStateException("Job count negative!!!");
        }
        this.threadPool.steal(this, 1);
        return true;
    }

    public int drain() {
        int count = this.jobCount.drainPermits();
        if (count == 0) {
            return 0;
        }

        if (this.jobCount2.addAndGet(-count) < 0) {
            throw new IllegalStateException("Job count negative!!!");
        }
        this.threadPool.steal(this, count);
        return count;
    }

    public boolean isAlive() {
        return this.alive;
    }
}
