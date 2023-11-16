package me.cortex.voxelmon.core.rendering.building;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import me.cortex.voxelmon.core.rendering.AbstractFarWorldRenderer;
import me.cortex.voxelmon.core.rendering.RenderTracker;
import me.cortex.voxelmon.core.world.WorldEngine;
import me.cortex.voxelmon.core.world.WorldSection;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.WorldChunk;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

public class RenderGenerationService {
    //TODO: make it accept either a section or section position and have a concurrent hashset to determine if
    // a section is in the build queue
    private record BuildTask(Supplier<WorldSection> sectionSupplier) {}

    private volatile boolean running = true;
    private final Thread[] workers;

    private final ConcurrentLinkedDeque<BuildTask> taskQueue = new ConcurrentLinkedDeque<>();
    private final Semaphore taskCounter = new Semaphore(0);

    private final WorldEngine world;
    private final RenderTracker tracker;

    public RenderGenerationService(WorldEngine world, RenderTracker tracker, int workers) {
        this.world = world;
        this.tracker = tracker;

        this.workers =  new Thread[workers];
        for (int i = 0; i < workers; i++) {
            this.workers[i] = new Thread(this::renderWorker);
            this.workers[i].setDaemon(true);
            this.workers[i].setName("Render generation service #" + i);
            this.workers[i].start();
        }
    }


    //TODO: add a generated render data cache
    private void renderWorker() {
        //Thread local instance of the factory
        var factory = new RenderDataFactory(this.world);
        while (this.running) {
            this.taskCounter.acquireUninterruptibly();
            if (!this.running) break;
            var task = this.taskQueue.pop();
            var section = task.sectionSupplier.get();
            if (section == null) {
                continue;
            }
            section.assertNotFree();
            int buildFlags = this.tracker.getBuildFlagsOrAbort(section);
            if (buildFlags != 0) {
                this.tracker.processBuildResult(factory.generateMesh(section, buildFlags));
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
    public void enqueueTask(int lvl, int x, int y, int z) {
        this.taskQueue.add(new BuildTask(()->{
            if (this.tracker.shouldStillBuild(lvl, x, y, z)) {
                return this.world.acquire(lvl, x, y, z);
            } else {
                return null;
            }
        }));
        this.taskCounter.release();
    }

    public void enqueueTask(WorldSection section) {
        //TODO: fixme! buildMask could have changed
        //if (!section.inRenderQueue.getAndSet(true)) {
        //    //TODO: add a boolean for needsRenderUpdate that can be set to false if the section is no longer needed
        //    // to be rendered, e.g. LoD level changed so that lod is no longer being rendered
        //    section.acquire();
        //    this.taskQueue.add(new BuildTask(()->section));
        //    this.taskCounter.release();
        //}
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

        //Wait for the ingest to finish
        while (this.taskCounter.availablePermits() != 0) {
            Thread.onSpinWait();
        }

        //Shutdown
        this.running = false;
        this.taskCounter.release(1000);
        //Wait for thread to join
        try {
            for (var worker : this.workers) {
                worker.join();
            }
        } catch (InterruptedException e) {throw new RuntimeException(e);}
    }
}
