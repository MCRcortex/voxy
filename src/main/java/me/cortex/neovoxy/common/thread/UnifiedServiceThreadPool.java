package me.cortex.neovoxy.common.thread;

import me.cortex.neovoxy.common.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class UnifiedServiceThreadPool {
    public final ServiceManager serviceManager;
    public final MultiThreadPrioritySemaphore groupSemaphore;

    private final MultiThreadPrioritySemaphore.Block selfBlock;
    private final ThreadGroup dedicatedPool;
    private final List<Thread> threads = new ArrayList<>();
    private int threadId = 0;

    public UnifiedServiceThreadPool() {
        this.dedicatedPool = new ThreadGroup("NeoVoxy Dedicated Service");
        this.serviceManager = new ServiceManager(this::release);
        this.groupSemaphore = new MultiThreadPrioritySemaphore(this.serviceManager::tryRunAJob);

        this.selfBlock = this.groupSemaphore.createBlock();
    }

    private final void release(int i) {this.groupSemaphore.pooledRelease(i);}

    public boolean setNumThreads(int threads) {
        synchronized (this.threads) {
            int diff = threads - this.threads.size();
            if (diff==0) return false;//Already correct
            if (diff<0) {//Remove threads
                this.selfBlock.release(-diff);
            } else {//Add threads
                for (int i = 0; i < diff; i++) {
                    var t = new Thread(this.dedicatedPool, this::workerThread, "Dedicated NeoVoxy Worker #"+(this.threadId++));
                    t.setPriority(3);
                    t.setDaemon(true);
                    this.threads.add(t);
                    t.start();
                }
            }
        }
        while (true) {
            synchronized (this.threads) {
                if (this.threads.size() == threads) return true;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void workerThread() {
        this.selfBlock.acquire();//This is stupid but it works

        //We are exiting, remove self from list of threads
        synchronized (this.threads) {
            this.threads.remove(Thread.currentThread());
        }
    }

    public void shutdown() {
        this.serviceManager.shutdown();
        this.selfBlock.release(10000);
        while (true) {
            synchronized (this.threads) {
                if (this.threads.isEmpty()) {
                    break;
                }
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        this.selfBlock.free();
    }


    public static void main(String[] args) {
        var ustp = new UnifiedServiceThreadPool();

        AtomicInteger cc = new AtomicInteger();
        AtomicInteger cnt = new AtomicInteger();

        var s1 = ustp.serviceManager.createService(()->{
            AtomicBoolean cleaned = new AtomicBoolean();
            AtomicInteger a = new AtomicInteger();
            return new Pair<>(()->{
                if (cleaned.get()) {
                    System.err.println("TRIED EXECUTING CLEANED CTX");
                } else {
                    a.incrementAndGet();
                    cnt.incrementAndGet();
                }
            }, ()->{
                if (cleaned.getAndSet(true)) {
                    System.err.println("TRIED DOUBLE CLEANING A VALUE");
                } else {
                    System.out.println("Cleaned ref, exec: " + a.get());
                    cc.incrementAndGet();
                }
            });
        }, 1);

        for (int i = 0; i < 1000; i++) {
            s1.execute();
        }
        ustp.setNumThreads(1);
        ustp.setNumThreads(10);
        ustp.setNumThreads(0);
        ustp.setNumThreads(1);
        s1.blockTillEmpty();
        s1.shutdown();
        ustp.shutdown();
        System.out.println(cnt);
    }
}
