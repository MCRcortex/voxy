package me.cortex.voxy.common.world.service;

import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;

//TODO:
//FIXME:
// FINISHME:
// Use this instead of seperate thread pools, use a single shared pool where tasks are submitted to and worked on

public class ServiceThreadPool {
    private volatile boolean running = true;
    private final Thread[] workers;
    private final Semaphore jobCounter = new Semaphore(0);
    //TODO: have a wrapper to specify extra information about the job for debugging
    private final ConcurrentLinkedDeque<Runnable> jobQueue = new ConcurrentLinkedDeque<>();


    public ServiceThreadPool(int workers) {
        this.workers = new Thread[workers];
        for (int i = 0; i < workers; i++) {
            var worker = new Thread(this::worker);
            worker.setDaemon(false);
            worker.setName("Service worker #" + i);
            worker.start();
            this.workers[i]  = worker;
        }
    }

    private void worker() {
        while (true) {
            this.jobCounter.acquireUninterruptibly();
            if (!this.running) {
                break;
            }
            var job = this.jobQueue.pop();
            try {
                job.run();
            } catch (Exception e) {
                System.err.println(e);
                MinecraftClient.getInstance().executeSync(()->
                        MinecraftClient.getInstance().player.sendMessage(
                                Text.literal(
                                        "Voxy ingester had an exception while executing service job please check logs and report error")));
            }
        }
    }


    public void shutdown() {
        //Wait for the tasks to finish
        while (this.jobCounter.availablePermits() != 0) {
            Thread.onSpinWait();
        }

        //Shutdown
        this.running = false;
        this.jobCounter.release(1000);

        //Wait for thread to join
        try {
            for (var worker : this.workers) {
                worker.join();
            }
        } catch (InterruptedException e) {throw new RuntimeException(e);}
    }
}
