package me.cortex.voxy.common.world.service;

import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.thread.Service;
import me.cortex.voxy.common.thread.ServiceManager;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;

import java.util.concurrent.ConcurrentLinkedDeque;

//TODO: add an option for having synced saving, that is when call enqueueSave, that will instead, instantly
// save to the db, this can be useful for just reducing the amount of thread pools in total
// might have some issues with threading if the same section is saved from multiple threads?
public class SectionSavingService {
    private final Service service;
    private record SaveEntry(WorldEngine engine, WorldSection section) {}
    private final ConcurrentLinkedDeque<SaveEntry> saveQueue = new ConcurrentLinkedDeque<>();

    public SectionSavingService(ServiceManager sm) {
        this.service = sm.createServiceNoCleanup(() -> this::processJob, 100, "Section saving service");
    }

    private void processJob() {
        var task = this.saveQueue.pop();
        var section = task.section;
        section.assertNotFree();
        try {
            //Unmark it dirty here (if it wasnt or w/e) so that it doesnt pointlessly resave (in theory this should be safe to do)
            section.setNotDirty();
            if (section.exchangeIsInSaveQueue(false)) {
                task.engine.storage.saveSection(section);
            }
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
        if (section.exchangeIsInSaveQueue(true)) {
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
                while (this.getTaskCount() > 5_000 && this.service.isLive()) {
                    if (!this.service.steal()) {
                        break;
                    }
                    this.processJob();
                }
            }

            this.saveQueue.add(new SaveEntry(in, section));
            this.service.execute();
        }
    }

    public void shutdown() {
        if (this.service.numJobs() != 0) {
            Logger.error("Voxy section saving still in progress, estimated " + this.service.numJobs() + " sections remaining.");
            this.service.blockTillEmpty();
        }
        this.service.shutdown();
        //Manually save any remaining entries
        while (!this.saveQueue.isEmpty()) {
            this.processJob();
        }
    }

    public int getTaskCount() {
        return this.service.numJobs();
    }
}
