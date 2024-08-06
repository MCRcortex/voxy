package me.cortex.voxy.common.world.thread;

import me.cortex.voxy.common.util.TrackedObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class ServiceSlice extends TrackedObject {
    private final String name;
    final int weightPerJob;
    private volatile boolean alive = true;
    private final ServiceThreadPool threadPool;
    private final Supplier<Runnable> workerGenerator;
    final Semaphore jobCount = new Semaphore(0);
    private final Runnable[] runningCtxs;
    private final AtomicInteger activeCount = new AtomicInteger();
    private final AtomicInteger jobCount2 = new AtomicInteger();
    private final BooleanSupplier condition;

    ServiceSlice(ServiceThreadPool threadPool, Supplier<Runnable> workerGenerator, String name, int weightPerJob, BooleanSupplier condition) {
        this.threadPool = threadPool;
        this.condition = condition;
        this.runningCtxs = new Runnable[threadPool.getThreadCount()];
        this.workerGenerator = workerGenerator;
        this.name = name;
        this.weightPerJob = weightPerJob;
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
            ctx = this.workerGenerator.get();
            this.runningCtxs[threadIndex] = ctx;
        }

        //Run the job
        try {
            ctx.run();
        } catch (Exception e) {
            System.err.println("Unexpected error occurred while executing a service job, expect things to break badly");
            e.printStackTrace();
            MinecraftClient.getInstance().execute(()->MinecraftClient.getInstance().player.sendMessage(Text.literal("A voxy service had an exception while executing please check logs and report error")));
        } finally {
            if (this.activeCount.decrementAndGet() < 0) {
                throw new IllegalStateException("Alive count negative!");
            }
            this.jobCount2.decrementAndGet();
        }
        return true;
    }

    //Tells the system that a single instance of this service needs executing
    public void execute() {
        if (!this.alive) {
            throw new IllegalStateException("Tried to do work on a dead service");
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

        super.free0();
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
}
