package me.cortex.voxy.client.core.rendering.building;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import me.cortex.voxy.client.core.model.IdNotYetComputedException;
import me.cortex.voxy.client.core.model.ModelBakerySubsystem;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.common.thread.ServiceSlice;
import me.cortex.voxy.common.thread.ServiceThreadPool;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

//TODO: Add a render cache
public class RenderGenerationService {
    public interface TaskChecker {boolean check(int lvl, int x, int y, int z);}
    private record BuildTask(long position, Supplier<WorldSection> sectionSupplier, boolean[] hasDoneModelRequest) {}

    private final Long2ObjectLinkedOpenHashMap<BuildTask> taskQueue = new Long2ObjectLinkedOpenHashMap<>();

    private final WorldEngine world;
    private final ModelBakerySubsystem modelBakery;
    private final Consumer<SectionUpdate> resultConsumer;
    private final boolean emitMeshlets;

    private final ServiceSlice threads;


    public RenderGenerationService(WorldEngine world, ModelBakerySubsystem modelBakery, ServiceThreadPool serviceThreadPool, Consumer<SectionUpdate> consumer, boolean emitMeshlets) {
        this.emitMeshlets = emitMeshlets;
        this.world = world;
        this.modelBakery = modelBakery;
        this.resultConsumer = consumer;

        this.threads = serviceThreadPool.createService("Section mesh generation service", 100, ()->{
            //Thread local instance of the factory
            var factory = new RenderDataFactory(this.world, this.modelBakery.factory, this.emitMeshlets);
            return () -> {
                this.processJob(factory);
            };
        });
    }

    //NOTE: the biomes are always fully populated/kept up to date

    //Asks the Model system to bake all blocks that currently dont have a model
    private void computeAndRequestRequiredModels(WorldSection section, int extraId) {
        var raw = section.copyData();//TODO: replace with copyDataTo and use a "thread local"/context array to reduce allocation rates
        IntOpenHashSet seen = new IntOpenHashSet(128);
        seen.add(extraId);
        for (long state : raw) {
            int block = Mapper.getBlockId(state);
            if (!this.modelBakery.factory.hasModelForBlockId(block)) {
                if (seen.add(block)) {
                    this.modelBakery.requestBlockBake(block);
                }
            }
        }
    }

    //TODO: add a generated render data cache
    private void processJob(RenderDataFactory factory) {
        BuildTask task;
        synchronized (this.taskQueue) {
            task = this.taskQueue.removeFirst();
        }
        long time = System.nanoTime();
        var section = task.sectionSupplier.get();
        if (section == null) {
            this.resultConsumer.accept(new SectionUpdate(task.position, time, BuiltSection.empty(task.position), (byte) 0));
            return;
        }
        section.assertNotFree();
        BuiltSection mesh = null;
        try {
            mesh = factory.generateMesh(section);
        } catch (IdNotYetComputedException e) {
            if (!this.modelBakery.factory.hasModelForBlockId(e.id)) {
                this.modelBakery.requestBlockBake(e.id);
            }
            if (task.hasDoneModelRequest[0]) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            } else {
                //The reason for the extra id parameter is that we explicitly add/check against the exception id due to e.g. requesting accross a chunk boarder wont be captured in the request
                this.computeAndRequestRequiredModels(section, e.id);
            }
            //We need to reinsert the build task into the queue
            //System.err.println("Render task failed to complete due to un-computed client id");
            synchronized (this.taskQueue) {
                var queuedTask = this.taskQueue.computeIfAbsent(section.key, (a)->task);
                queuedTask.hasDoneModelRequest[0] = true;//Mark (or remark) the section as having chunks requested

                if (queuedTask == task) {//use the == not .equal to see if we need to release a permit
                    this.threads.execute();//Since we put in queue, release permit
                }
            }
        }

        byte childMask = section.getNonEmptyChildren();
        section.release();
        //Time is the time at the start of the update
        this.resultConsumer.accept(new SectionUpdate(section.key, time, mesh, childMask));
    }

    public void enqueueTask(int lvl, int x, int y, int z) {
        this.enqueueTask(lvl, x, y, z, (l,x1,y1,z1)->true);
    }

    public void enqueueTask(long position) {
        this.enqueueTask(position, (l,x1,y1,z1)->true);
    }

    public void enqueueTask(int lvl, int x, int y, int z, TaskChecker checker) {
        this.enqueueTask(WorldEngine.getWorldSectionId(lvl, x, y, z), checker);
    }

    public void enqueueTask(long ikey, TaskChecker checker) {
        synchronized (this.taskQueue) {
            this.taskQueue.computeIfAbsent(ikey, key->{
                this.threads.execute();
                return new BuildTask(ikey, ()->{
                    if (checker.check(WorldEngine.getLevel(ikey), WorldEngine.getX(ikey), WorldEngine.getY(ikey), WorldEngine.getZ(ikey))) {
                        return this.world.acquireIfExists(WorldEngine.getLevel(ikey), WorldEngine.getX(ikey), WorldEngine.getY(ikey), WorldEngine.getZ(ikey));
                    } else {
                        return null;
                    }
                }, new boolean[1]);
            });
        }
    }

    public int getTaskCount() {
        return this.threads.getJobCount();
    }

    public void shutdown() {
        this.threads.shutdown();

        //Cleanup any remaining data
        while (!this.taskQueue.isEmpty()) {
            this.taskQueue.removeFirst();
        }
    }

    public void addDebugData(List<String> debug) {
        debug.add("RMQ: " + this.taskQueue.size());//render mesh queue
    }
}
