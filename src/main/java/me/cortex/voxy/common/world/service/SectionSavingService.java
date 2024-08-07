package me.cortex.voxy.common.world.service;

import me.cortex.voxy.common.world.SaveLoadSystem;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.thread.ServiceSlice;
import me.cortex.voxy.common.thread.ServiceThreadPool;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.concurrent.ConcurrentLinkedDeque;

//TODO: add an option for having synced saving, that is when call enqueueSave, that will instead, instantly
// save to the db, this can be useful for just reducing the amount of thread pools in total
// might have some issues with threading if the same section is saved from multiple threads?
public class SectionSavingService {
    private final ServiceSlice threads;
    private final ConcurrentLinkedDeque<WorldSection> saveQueue = new ConcurrentLinkedDeque<>();
    private final WorldEngine world;

    public SectionSavingService(WorldEngine worldEngine, ServiceThreadPool threadPool) {
        this.world = worldEngine;
        this.threads = threadPool.createService("Section saving service", 100, () -> this::processJob);
    }

    private void processJob() {
        var section = this.saveQueue.pop();
        section.assertNotFree();
        try {
            section.inSaveQueue.set(false);
            var saveData = SaveLoadSystem.serialize(section);
            this.world.storage.setSectionData(section.key, saveData);
            saveData.free();
        } catch (Exception e) {
            e.printStackTrace();
            MinecraftClient.getInstance().executeSync(()->MinecraftClient.getInstance().player.sendMessage(Text.literal("Voxy saver had an exception while executing please check logs and report error")));
        }
        section.release();
    }

    public void enqueueSave(WorldSection section) {
        //If its not enqueued for saving then enqueue it
        if (!section.inSaveQueue.getAndSet(true)) {
            //Acquire the section for use
            section.acquire();
            this.saveQueue.add(section);
            this.threads.execute();
        }
    }

    public void shutdown() {
        if (this.threads.getJobCount() != 0) {
            System.err.println("Voxy section saving still in progress, estimated " + this.threads.getJobCount() + " sections remaining.");
            while (this.threads.getJobCount() != 0) {
                Thread.onSpinWait();
            }
        }
        this.threads.shutdown();
        //Manually save any remaining entries
        while (!this.saveQueue.isEmpty()) {
            this.processJob();
        }
    }

    public int getTaskCount() {
        return this.threads.getJobCount();
    }
}
