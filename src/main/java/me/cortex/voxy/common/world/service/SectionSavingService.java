package me.cortex.voxy.common.world.service;

import me.cortex.voxy.common.Logger;
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
    private record SaveEntry(WorldEngine engine, WorldSection section) {}
    private final ConcurrentLinkedDeque<SaveEntry> saveQueue = new ConcurrentLinkedDeque<>();

    public SectionSavingService(ServiceThreadPool threadPool) {
        this.threads = threadPool.createServiceNoCleanup("Section saving service", 100, () -> this::processJob);
    }

    private void processJob() {
        var task = this.saveQueue.pop();
        var section = task.section;
        section.assertNotFree();
        try {
            section.inSaveQueue.set(false);
            task.engine.storage.saveSection(section);
        } catch (Exception e) {
            Logger.error("Voxy saver had an exception while executing please check logs and report error", e);
        }
        section.release();
    }

    /*
    public void enqueueSave(WorldSection section) {
        if (section._getSectionTracker() != null && section._getSectionTracker().engine != null) {
            this.enqueueSave(section._getSectionTracker().engine, section);
        } else {
            Logger.error("Tried saving world section, but did not have world associated");
        }
    }*/

    public void enqueueSave(WorldEngine in, WorldSection section) {
        //If its not enqueued for saving then enqueue it
        if (!section.inSaveQueue.getAndSet(true)) {
            //Acquire the section for use
            section.acquire();

            //Hard limit the save count to prevent OOM
            if (this.getTaskCount() > 5_000) {
                //wait a bit
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                //If we are still full, process entries in the queue ourselves instead of waiting for the service
                while (this.getTaskCount() > 5_000 && this.threads.isAlive()) {
                    if (!this.threads.steal()) {
                        break;
                    }
                    this.processJob();
                }
            }

            this.saveQueue.add(new SaveEntry(in, section));
            this.threads.execute();
        }
    }

    public void shutdown() {
        if (this.threads.getJobCount() != 0) {
            Logger.error("Voxy section saving still in progress, estimated " + this.threads.getJobCount() + " sections remaining.");
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
