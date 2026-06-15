package me.cortex.neovoxy.client.core.model;


import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import me.cortex.neovoxy.common.Logger;
import me.cortex.neovoxy.common.world.other.Mapper;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;

public class ModelBakerySubsystem {
    //Redo to just make it request the block faces with the async texture download stream which
    // basicly solves all the render stutter due to the baking

    private final ModelStore storage = new ModelStore();
    public final ModelFactory factory;
    private final Mapper mapper;
    private final AtomicInteger blockIdCount = new AtomicInteger();
    private final ConcurrentLinkedDeque<Integer> blockIdQueue = new ConcurrentLinkedDeque<>();//TODO: replace with custom DS

    private final Thread processingThread;
    private volatile boolean isRunning = true;
    public ModelBakerySubsystem(Mapper mapper) {
        this.mapper = mapper;
        this.factory = new ModelFactory(mapper, this.storage);
        this.processingThread = new Thread(()->{//TODO replace this with something good/integrate it into the async processor so that we just have less threads overall
            while (this.isRunning) {
                this.factory.processAllThings();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, "Model factory processor");
        this.processingThread.start();
    }

    public void tick(long totalBudget) {
        long start = System.nanoTime();
        this.factory.tickAndProcessUploads();
        //Always do 1 iteration minimum
        Integer i = this.blockIdQueue.poll();
        if (i != null) {
            int j = 0;
            if (i != null) {
                int fbBinding = glGetInteger(GL_FRAMEBUFFER_BINDING);

                do {
                    this.factory.addEntry(i);
                    j++;
                    if (4<j&&(totalBudget<(System.nanoTime() - start)+50_000))//20<j||
                        break;
                    i = this.blockIdQueue.poll();
                } while (i != null);

                glBindFramebuffer(GL_FRAMEBUFFER, fbBinding);//This is done here as stops needing to set then unset the fb in the thing 1000x
            }
            this.blockIdCount.addAndGet(-j);
        }

        //TimingStatistics.modelProcess.stop();
    }

    public void shutdown() {
        this.isRunning = false;
        try {
            this.processingThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        this.factory.free();
        this.storage.free();
    }

    //This is on this side only and done like this as only worker threads call this code
    private final ReentrantLock seenIdsLock = new ReentrantLock();
    private final IntOpenHashSet seenIds = new IntOpenHashSet(6000);//TODO: move to a lock free concurrent hashmap
    public void requestBlockBake(int blockId) {
        if (this.mapper.getBlockStateCount() < blockId) {
            Logger.error("Error, got bakeing request for out of range state id. StateId: " + blockId + " max id: " + this.mapper.getBlockStateCount(), new Exception());
            return;
        }
        this.seenIdsLock.lock();
        if (!this.seenIds.add(blockId)) {
            this.seenIdsLock.unlock();
            return;
        }
        this.seenIdsLock.unlock();
        this.blockIdQueue.add(blockId);
        this.blockIdCount.incrementAndGet();
    }

    public void addBiome(Mapper.BiomeEntry biomeEntry) {
        this.factory.addBiome(biomeEntry);
    }

    public void addDebugData(List<String> debug) {
        debug.add(String.format("MQ/IF/MC: %04d, %03d, %04d", this.blockIdCount.get(), this.factory.getInflightCount(),  this.factory.getBakedCount()));//Model bake queue/in flight/model baked count
    }

    public ModelStore getStore() {
        return this.storage;
    }

    public boolean areQueuesEmpty() {
        return this.blockIdCount.get()==0 && this.factory.getInflightCount() == 0;
    }

    public int getProcessingCount() {
        return this.blockIdCount.get() + this.factory.getInflightCount();
    }
}
