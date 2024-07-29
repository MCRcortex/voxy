package me.cortex.voxy.client.core.model;


import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
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

//Uses an off thread gl context to do the model baking on
public class OffThreadModelBakerySystem {
    //NOTE: Create a static final context offthread and dont close it, just reuse the context, since context creation is expensive
    private static final long GL_CTX;
    private static final GLCapabilities GL_CAPS;
    static {
        var caps = GL.getCapabilities();
        GLFW.glfwMakeContextCurrent(0L);
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, 0);
        GL_CTX = GLFW.glfwCreateWindow(1, 1, "", 0, MinecraftClient.getInstance().getWindow().getHandle());
        GLFW.glfwMakeContextCurrent(GL_CTX);
        GL_CAPS = GL.createCapabilities();
        GLFW.glfwMakeContextCurrent(MinecraftClient.getInstance().getWindow().getHandle());
        GL.setCapabilities(caps);
    }



    private final ModelStore storage = new ModelStore(16);
    public final ModelFactory factory;
    private final ConcurrentLinkedDeque<NewModelBufferDelta> bufferDeltas = new ConcurrentLinkedDeque<>();
    private final IntLinkedOpenHashSet blockIdQueue = new IntLinkedOpenHashSet();
    private final Semaphore queueCounter = new Semaphore(0);



    private final Thread bakingThread;
    private volatile boolean running = true;
    public OffThreadModelBakerySystem(Mapper mapper) {
        this.factory = new ModelFactory(mapper);
        this.bakingThread = new Thread(this::bakeryThread);
        this.bakingThread.setName("Baking thread");
        this.bakingThread.setPriority(3);
        this.bakingThread.start();
    }

    private void bakeryThread() {
        GLFW.glfwMakeContextCurrent(GL_CTX);
        GL.setCapabilities(GL_CAPS);

        //FIXME: tile entities will probably need to be baked on the main render thread
        while (true) {
            this.queueCounter.acquireUninterruptibly();
            if (!this.running) break;
            int blockId;
            synchronized (this.blockIdQueue) {
                blockId = this.blockIdQueue.removeFirstInt();
                VarHandle.fullFence();//Ensure memory coherancy
            }

            this.factory.addEntry(blockId);
        }
    }

    //Changes to the block table/atlas must be run on the main render thread
    public void syncChanges() {
        //TODO: FIXME!! the ordering of the uploads here and the order that _both_ model AND biome are done in the bakery thread MUST be the same!!!
    }

    public void shutdown() {
        this.running = false;
        this.queueCounter.release(999);
        try {
            this.bakingThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        this.factory.free();
        this.storage.free();
    }

    public void requestBlockBake(int blockId) {
        synchronized (this.blockIdQueue) {
            if (this.blockIdQueue.add(blockId)) {
                VarHandle.fullFence();//Ensure memory coherancy
                this.queueCounter.release(1);
            }
        }
    }

    public void addDebugData(List<String> debug) {

    }
}
