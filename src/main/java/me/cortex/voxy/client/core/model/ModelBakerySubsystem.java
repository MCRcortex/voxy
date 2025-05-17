package me.cortex.voxy.client.core.model;


import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import me.cortex.voxy.client.TimingStatistics;
import me.cortex.voxy.client.core.gl.GlFramebuffer;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.util.RawDownloadStream;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;

import static org.lwjgl.opengl.ARBFramebufferObject.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;
import static org.lwjgl.opengl.GL45.glBlitNamedFramebuffer;

public class ModelBakerySubsystem {
    //Redo to just make it request the block faces with the async texture download stream which
    // basicly solves all the render stutter due to the baking

    private final ModelStore storage = new ModelStore();
    public final ModelFactory factory;
    private final AtomicInteger blockIdCount = new AtomicInteger();
    private final ConcurrentLinkedDeque<Integer> blockIdQueue = new ConcurrentLinkedDeque<>();//TODO: replace with custom DS
    private final ConcurrentLinkedDeque<Mapper.BiomeEntry> biomeQueue = new ConcurrentLinkedDeque<>();

    public ModelBakerySubsystem(Mapper mapper) {
        this.factory = new ModelFactory(mapper, this.storage);
    }

    public void tick(long totalBudget) {
        //Upload all biomes
        while (!this.biomeQueue.isEmpty()) {
            var biome = this.biomeQueue.poll();
            var biomeReg = MinecraftClient.getInstance().world.getRegistryManager().getOrThrow(RegistryKeys.BIOME);
            this.factory.addBiome(biome.id, biomeReg.get(Identifier.of(biome.biome)));
        }


        /*
        //There should be a method to access the frame time IIRC, if the user framecap is unlimited lock it to like 60 fps for computation
        int BUDGET = 16;//TODO: make this computed based on the remaining free time in a frame (and like div by 2 to reduce overhead) (with a min of 1)
        if (!this.blockIdQueue.isEmpty()) {
            int[] est = new int[Math.min(this.blockIdQueue.size(), BUDGET)];
            int i = 0;
            synchronized (this.blockIdQueue) {
                for (;i < est.length && !this.blockIdQueue.isEmpty(); i++) {
                    int blockId = this.blockIdQueue.removeFirstInt();
                    if (blockId == -1) {
                        i--;
                        continue;
                    }
                    est[i] = blockId;
                }
            }

            for (int j = 0; j < i; j++) {
                this.factory.addEntry(est[j]);
            }
        }*/
        //TimingStatistics.modelProcess.start();
        long start = System.nanoTime();
        VarHandle.fullFence();
        if (this.blockIdCount.get() != 0) {
            long budget = Math.min(totalBudget-150_000, totalBudget-(this.factory.resultJobs.size()*10_000L))-150_000;

            //Always do 1 iteration minimum
            Integer i = this.blockIdQueue.poll();
            int j = 0;
            if (i != null) {
                int fbBinding = glGetInteger(GL_FRAMEBUFFER_BINDING);

                do {
                    this.factory.addEntry(i);
                    j++;
                    if (24<j)//budget<(System.nanoTime() - start)+1000
                        break;
                    i = this.blockIdQueue.poll();
                } while (i != null);

                glBindFramebuffer(GL_FRAMEBUFFER, fbBinding);//This is done here as stops needing to set then unset the fb in the thing 1000x
            }
            this.blockIdCount.addAndGet(-j);
        }

        this.factory.tick();

        start = System.nanoTime();
        while (!this.factory.resultJobs.isEmpty()) {
            this.factory.resultJobs.poll().run();
            if (totalBudget<(System.nanoTime()-start))
                break;
        }
        //TimingStatistics.modelProcess.stop();
    }

    public void shutdown() {
        this.factory.free();
        this.storage.free();
    }

    //This is on this side only and done like this as only worker threads call this code
    private final StampedLock seenIdsLock = new StampedLock();
    private final IntOpenHashSet seenIds = new IntOpenHashSet(6000);
    public void requestBlockBake(int blockId) {
        long stamp = this.seenIdsLock.writeLock();
        if (!this.seenIds.add(blockId)) {
            this.seenIdsLock.unlockWrite(stamp);
            return;
        }
        this.seenIdsLock.unlockWrite(stamp);
        this.blockIdQueue.add(blockId);
        this.blockIdCount.incrementAndGet();
    }

    public void addBiome(Mapper.BiomeEntry biomeEntry) {
        this.biomeQueue.add(biomeEntry);
    }

    public void addDebugData(List<String> debug) {
        debug.add(String.format("MQ/IF/MC: %04d, %03d, %04d", this.blockIdCount.get(), this.factory.getInflightCount(),  this.factory.getBakedCount()));//Model bake queue/in flight/model baked count
    }

    public ModelStore getStore() {
        return this.storage;
    }

    public boolean areQueuesEmpty() {
        return this.blockIdCount.get()==0 && this.factory.getInflightCount() == 0 && this.biomeQueue.isEmpty();
    }

    public int getProcessingCount() {
        return this.blockIdCount.get() + this.factory.getInflightCount();
    }
}
