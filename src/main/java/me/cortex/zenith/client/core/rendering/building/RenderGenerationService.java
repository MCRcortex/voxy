package me.cortex.zenith.client.core.rendering.building;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import me.cortex.zenith.common.world.WorldEngine;
import me.cortex.zenith.common.world.WorldSection;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

//TODO: Add a render cache
public class RenderGenerationService {
    public interface TaskChecker {boolean check(int lvl, int x, int y, int z);}
    private record BuildTask(Supplier<WorldSection> sectionSupplier, ToIntFunction<WorldSection> flagSupplier) {}

    private volatile boolean running = true;
    private final Thread[] workers;

    private final Long2ObjectLinkedOpenHashMap<BuildTask> taskQueue = new Long2ObjectLinkedOpenHashMap<>();

    private final Semaphore taskCounter = new Semaphore(0);
    private final WorldEngine world;
    private final Consumer<BuiltSectionGeometry> resultConsumer;

    public RenderGenerationService(WorldEngine world, int workers, Consumer<BuiltSectionGeometry> consumer) {
        this.world = world;
        this.resultConsumer = consumer;
        this.workers =  new Thread[workers];
        for (int i = 0; i < workers; i++) {
            this.workers[i] = new Thread(this::renderWorker);
            this.workers[i].setDaemon(true);
            this.workers[i].setName("Render generation service #" + i);
            this.workers[i].start();
        }
    }

    private final ConcurrentHashMap<Long, BuiltSectionGeometry> renderCache = new ConcurrentHashMap<>(1000,0.75f,10);

    //TODO: add a generated render data cache
    private void renderWorker() {
        //Thread local instance of the factory
        var factory = new RenderDataFactory(this.world);
        while (this.running) {
            this.taskCounter.acquireUninterruptibly();
            if (!this.running) break;
            BuildTask task;
            synchronized (this.taskQueue) {
                task = this.taskQueue.removeFirst();
            }
            var section = task.sectionSupplier.get();
            if (section == null) {
                continue;
            }
            section.assertNotFree();
            int buildFlags = task.flagSupplier.applyAsInt(section);
            if (buildFlags != 0) {
                var mesh = factory.generateMesh(section, buildFlags);
                this.resultConsumer.accept(mesh.clone());

                if (false) {
                    var prevCache = this.renderCache.put(mesh.position, mesh);
                    if (prevCache != null) {
                        prevCache.free();
                    }
                } else {
                    mesh.free();
                }
            }
            section.release();
        }
    }

    //TODO: Add a priority system, higher detail sections must always be updated before lower detail
    // e.g. priorities NONE->lvl0 and lvl1 -> lvl0 over lvl0 -> lvl1


    //TODO: make it pass either a world section, _or_ coodinates so that the render thread has to do the loading of the sections
    // not the calling method

    //TODO: maybe make it so that it pulls from the world to stop the inital loading absolutly butt spamming the queue
    // and thus running out of memory

    //TODO: REDO THIS ENTIRE THING
    // render tasks should not be bound to a WorldSection, instead it should be bound to either a WorldSection or
    // an LoD position, the issue is that if we bound to a LoD position we loose all the info of the WorldSection
    // like if its in the render queue and if we should abort building the render data
    //1 proposal fix is a Long2ObjectLinkedOpenHashMap<WorldSection> which means we can abort if needed,
    // also gets rid of dependency on a WorldSection (kinda)
    public void enqueueTask(int lvl, int x, int y, int z, ToIntFunction<WorldSection> flagSupplier) {
        this.enqueueTask(lvl, x, y, z, (l,x1,y1,z1)->true, flagSupplier);
    }

    public void enqueueTask(int lvl, int x, int y, int z, TaskChecker checker, ToIntFunction<WorldSection> flagSupplier) {
        long ikey = WorldEngine.getWorldSectionId(lvl, x, y, z);
        {
            var cache = this.renderCache.get(ikey);
            if (cache != null) {
                this.resultConsumer.accept(cache.clone());
                return;
            }
        }
        synchronized (this.taskQueue) {
            this.taskQueue.computeIfAbsent(ikey, key->{
                this.taskCounter.release();
                return new BuildTask(()->{
                    if (checker.check(lvl, x, y, z)) {
                        return this.world.acquireIfExists(lvl, x, y, z);
                    } else {
                        return null;
                    }
                }, flagSupplier);
            });
        }
    }

    public void removeTask(int lvl, int x, int y, int z) {
        synchronized (this.taskQueue) {
            if (this.taskQueue.remove(WorldEngine.getWorldSectionId(lvl, x, y, z)) != null) {
                this.taskCounter.acquireUninterruptibly();
            }
        }
    }

    public int getTaskCount() {
        return this.taskCounter.availablePermits();
    }

    public void shutdown() {
        boolean anyAlive = false;
        for (var worker : this.workers) {
            anyAlive |= worker.isAlive();
        }

        if (!anyAlive) {
            System.err.println("Render gen workers already dead on shutdown! this is very very bad, check log for errors from this thread");
            return;
        }

        //Since this is just render data, dont care about any tasks needing to finish
        this.running = false;
        this.taskCounter.release(1000);

        //Wait for thread to join
        try {
            for (var worker : this.workers) {
                worker.join();
            }
        } catch (InterruptedException e) {throw new RuntimeException(e);}

        //Cleanup any remaining data
        while (!this.taskQueue.isEmpty()) {
            this.taskQueue.removeFirst();
        }
        this.renderCache.values().forEach(BuiltSectionGeometry::free);
    }
}
