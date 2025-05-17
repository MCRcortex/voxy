package me.cortex.voxy.common.thread;

import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.Pair;
import me.cortex.voxy.common.util.ThreadUtils;

import java.lang.invoke.VarHandle;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;


//TODO: could also probably replace all of this with just VirtualThreads and a Executors.newThreadPerTaskExecutor with a fixed thread pool
// it is probably better anyway
public class ServiceThreadPool {
    private volatile boolean running = true;
    private volatile boolean releaseNow = false;
    private Thread[] workers = new Thread[0];
    private final Semaphore jobCounter = new Semaphore(0);

    private volatile ServiceSlice[] serviceSlices = new ServiceSlice[0];
    private final AtomicLong totalJobWeight = new AtomicLong();
    private final ThreadGroup threadGroup;

    public ServiceThreadPool(int threadCount) {
        this(threadCount, 1);//Maybe change to 3
    }

    public ServiceThreadPool(int threadCount, int priority) {
        this.threadGroup = new ThreadGroup("Service job workers");
        this.workers = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            int threadId = i;
            var worker = new Thread(this.threadGroup, ()->this.worker(threadId));
            worker.setDaemon(false);
            worker.setName("Service worker #" + i);
            worker.setPriority(priority);
            worker.start();
            worker.setUncaughtExceptionHandler(this::handleUncaughtException);
            this.workers[i]  = worker;
        }
    }

    public ServiceSlice createServiceNoCleanup(String name, int weight, Supplier<Runnable> workGenerator) {
        return this.createService(name, weight, ()->new Pair<>(workGenerator.get(), null));
    }

    public ServiceSlice createServiceNoCleanup(String name, int weight, Supplier<Runnable> workGenerator, BooleanSupplier executionCondition) {
        return this.createService(name, weight, ()->new Pair<>(workGenerator.get(), null), executionCondition);
    }

    public synchronized ServiceSlice createService(String name, int weight, Supplier<Pair<Runnable, Runnable>> workGenerator) {
        return this.createService(name, weight, workGenerator, ()->true);
    }

    public synchronized ServiceSlice createService(String name, int weight, Supplier<Pair<Runnable, Runnable>> workGenerator, BooleanSupplier executionCondition) {
        var service = new ServiceSlice(this, workGenerator, name, weight, executionCondition);
        this.insertService(service);
        return service;
    }

    private void insertService(ServiceSlice service) {
        var current = this.serviceSlices;
        var newList = new ServiceSlice[current.length + 1];
        System.arraycopy(current, 0, newList, 0, current.length);
        newList[current.length] = service;
        this.serviceSlices = newList;
    }

    synchronized void removeService(ServiceSlice service) {
        this.removeServiceFromArray(service);
        int permits = service.jobCount.drainPermits();
        if (this.totalJobWeight.addAndGet(-((long) service.weightPerJob) * permits) < 0) {
            throw new IllegalStateException("Total job weight negative!");
        }

        //Need to acquire all the shut-down jobs
        try {
            //Wait for 1000 millis, to let shinanigans filter down
            if (!this.jobCounter.tryAcquire(permits, 1000, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Failed to acquire all the permits for the shut down jobs");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private synchronized void removeServiceFromArray(ServiceSlice service) {
        var lst = this.serviceSlices;
        int idx;
        for (idx = 0; idx < lst.length; idx++) {
            if (lst[idx] == service) {
                break;
            }
        }
        if (idx == lst.length) {
            throw new IllegalStateException("Service not in service list");
        }

        //Remove the slice from the array and set it back

        if (lst.length-1 == 0) {
            this.serviceSlices = new ServiceSlice[0];
            return;
        }

        ServiceSlice[] newArr = new ServiceSlice[lst.length-1];
        System.arraycopy(lst, 0, newArr, 0, idx);
        if (lst.length-1 != idx) {
            //Need to do a second copy
            System.arraycopy(lst, idx+1, newArr, idx, newArr.length-idx);
        }
        this.serviceSlices = newArr;
    }

    void execute(ServiceSlice service) {
        this.totalJobWeight.addAndGet(service.weightPerJob);
        this.jobCounter.release(1);
    }

    void steal(ServiceSlice service, int count) {
        this.totalJobWeight.addAndGet(-(service.weightPerJob*(long)count));
        this.releaseNow = true;
        for (int i = 0; i < count; i++) {
            this.jobCounter.acquireUninterruptibly();
        }
        this.releaseNow = false;
    }

    private void worker(int threadId) {
        ThreadUtils.SetSelfThreadPriorityWin32(ThreadUtils.WIN32_THREAD_PRIORITY_LOWEST);
        //ThreadUtils.SetSelfThreadPriorityWin32(ThreadUtils.WIN32_THREAD_MODE_BACKGROUND_BEGIN);

        long[] seed = new long[]{1234342^(threadId*124987198651981L+215987981111L)};
        int[] revolvingSelector = new int[1];
        long[] logIO = new long[] {0, System.currentTimeMillis()};
        while (true) {
            this.jobCounter.acquireUninterruptibly();
            if (!this.running) {
                break;
            }
            //This is because of JIT moment (it cant really replace methods while they are executing afak)
            this.worker_work(threadId, seed, revolvingSelector, logIO);
        }
    }

    private void worker_work(int threadId, long[] seedIO, int[] revolvingSelectorIO, long[] logIO) {
        final int ATTEMPT_COUNT = 50;
        int attempts = ATTEMPT_COUNT;
        while (true) {
            if (attempts < ATTEMPT_COUNT-2) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            if (this.releaseNow) {
                this.jobCounter.release();
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                break;
            }
            var ref = this.serviceSlices;
            if (ref.length == 0) {
                Logger.error("Service worker tried to run but had 0 slices");
                break;
            }
            if (attempts-- == 0) {
                Logger.warn("Unable to execute service after many attempts, releasing");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                this.jobCounter.release();//Release the job we acquired
                break;
            }
            long seed = seedIO[0]*1984691871L+1497210975L;
            seed = (seed ^ (seed >>> 30)) * -4658895280553007687L;
            seed = (seed ^ (seed >>> 27)) * -7723592293110705685L;
            seedIO[0] = seed;
            long clamped = seed&((1L<<63)-1);
            long weight = this.totalJobWeight.get();
            if (weight == 0) {
                this.jobCounter.release();
                break;
            }

            ServiceSlice service = null;
            for (int i = 0; i < ref.length; i++) {
                var service2 = ref[(int) ((clamped+i) % ref.length)];
                if (service2.hasJobs() && service2.workConditionMet()) {
                    service = service2;
                    break;
                }
            }
            if (service == null) {
                logIO[0]++;
                long delta = System.currentTimeMillis()-logIO[1];
                if (delta>30_000) {
                    logIO[1] = System.currentTimeMillis();
                    Logger.warn("No available jobs, sleeping releasing returning: " + (delta/1000) + " attempts " + logIO[0]);
                    logIO[0] = 0;
                }
                try {
                    Thread.sleep((long) (500*Math.random()+200));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                this.jobCounter.release();
                break;
            }

            //1 in 64 chance just to pick a service that has a task, in a cycling manor, this is to keep at least one service from overloading all services constantly
            if (((seed>>10)&63) == 0) {
                int revolvingSelector = revolvingSelectorIO[0];
                for (int i = 0; i < ref.length; i++) {
                    int idx = (i+revolvingSelector)%ref.length;
                    var slice = ref[idx];
                    if (slice.hasJobs() && slice.workConditionMet()) {
                        service = slice;
                        revolvingSelector = (idx+1)%ref.length;
                        break;
                    }
                }
                revolvingSelectorIO[0] = revolvingSelector;
            } else {
                long chosenNumber = clamped % weight;
                for (var slice : ref) {
                    chosenNumber -= ((long) slice.weightPerJob) * slice.jobCount.availablePermits();
                    if (chosenNumber <= 0 && slice.workConditionMet()) {
                        service = slice;
                        break;
                    }
                }
            }

            //Run the job
            if (!service.doRun(threadId)) {
                //Didnt consume the job, find a new job
                continue;
            }

            //Consumed a job from the service, decrease weight by the amount
            if (this.totalJobWeight.addAndGet(-service.weightPerJob)<0) {
                throw new IllegalStateException("Total job weight is negative");
            }

            //Sleep for a bit after running a job, yeild the thread
            Thread.yield();
            break;
        }
    }

    private void handleUncaughtException(Thread thread, Throwable throwable) {
        System.err.println("Service worker thread has exploded unexpectedly! this is really not good very very bad.");
        throwable.printStackTrace();
    }

    public void shutdown() {
        if (this.serviceSlices.length != 0) {
            String remaining = "";
            for (var service : this.serviceSlices) {
                remaining += service.name + ", ";
            }
            throw new IllegalStateException("All service slices must be shutdown before thread pool can exit. Remaining: " + remaining);
        }

        //Wait for the tasks to finish
        while (this.jobCounter.availablePermits() != 0) {
            Thread.onSpinWait();
        }

        int remainingJobs = this.jobCounter.drainPermits();
        //Shutdown
        this.running = false;
        this.jobCounter.release(1000);

        //Wait for thread to join
        try {
            for (var worker : this.workers) {
                worker.join();
            }
        } catch (InterruptedException e) {throw new RuntimeException(e);}

        if (this.totalJobWeight.get() != 0) {
            throw new IllegalStateException("Service pool job weight not 0 after shutdown");
        }

        if (remainingJobs != 0) {
            throw new IllegalStateException("Service thread pool had jobs remaining!");
        }
    }

    public int getThreadCount() {
        return this.workers.length;
    }

    /*
    public void setThreadCount(int threadCount) {
        if (threadCount == this.workers.length) {
            return;//No change
        }

        if (threadCount < this.workers.length) {
            //Need to remove workers
        } else {
            //Need to add new workers
        }

        this.workers = new Thread[threadCount];
        for (int i = 0; i < workers; i++) {
            int threadId = i;
            var worker = new Thread(this.threadGroup, ()->this.worker(threadId));
            worker.setDaemon(false);
            worker.setName("Service worker #" + i);
            worker.start();
            worker.setUncaughtExceptionHandler(this::handleUncaughtException);
            this.workers[i]  = worker;
        }
    }
     */
}
