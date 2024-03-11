package me.cortex.voxy.common.util;

public class ThreadPool {
    private volatile boolean running = true;
    private final ThreadGroup group;
    private final Thread[] workers;
    public ThreadPool(String name, int threads) {
        this.group = new ThreadGroup("Thread pool: " + name);
        this.workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            var worker = new Thread(this.group, this::worker);
            worker.setDaemon(false);
            worker.setName("Worker #" + i);
            worker.start();
            this.workers[i] = worker;
        }
    }

    private void worker() {
        while (running) {

        }
    }

    public void shutdown() {
    }
}
