package me.cortex.voxy.client.core.model;


import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;
public class OnThreadModelBakerySystem {

    private final ModelStore storage = new ModelStore(16);
    public final ModelFactory factory;
    private final IntLinkedOpenHashSet blockIdQueue = new IntLinkedOpenHashSet();

    public OnThreadModelBakerySystem(Mapper mapper) {
        this.factory = new ModelFactory(mapper);
    }

    public void tick() {
        //There should be a method to access the frame time IIRC, if the user framecap is unlimited lock it to like 60 fps for computation
        int BUDGET = 20;//TODO: make this computed based on the remaining free time in a frame (and like div by 2 to reduce overhead) (with a min of 1)
        for (int i = 0; i < BUDGET; i++) {
            if (!this.blockIdQueue.isEmpty()) {
                int blockId = -1;
                synchronized (this.blockIdQueue) {
                    if (!this.blockIdQueue.isEmpty()) {
                        blockId = this.blockIdQueue.removeFirstInt();
                        VarHandle.fullFence();//Ensure memory coherancy
                    } else {
                        break;
                    }
                }
                if (blockId != -1) {
                    this.factory.addEntry(blockId);
                }
            } else {
                break;
            }
        }
    }

    public void shutdown() {
        this.factory.free();
        this.storage.free();
    }

    public void requestBlockBake(int blockId) {
        synchronized (this.blockIdQueue) {
            if (this.blockIdQueue.add(blockId)) {
                VarHandle.fullFence();//Ensure memory coherancy
            }
        }
    }

    public void addDebugData(List<String> debug) {
        debug.add("MBQ/MBC: " + this.blockIdQueue.size() + "/"+ this.factory.getBakedCount());//Model bake queue/model baked count
    }
}
