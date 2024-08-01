package me.cortex.voxy.client.core.model;


import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import me.cortex.voxy.client.core.gl.GlFramebuffer;
import me.cortex.voxy.client.core.rendering.util.RawDownloadStream;
import me.cortex.voxy.common.world.other.Mapper;
import org.lwjgl.opengl.GL11;

import java.lang.invoke.VarHandle;
import java.util.List;

import static org.lwjgl.opengl.ARBFramebufferObject.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL45.glBlitNamedFramebuffer;

public class ModelBakerySubsystem {
    //Redo to just make it request the block faces with the async texture download stream which
    // basicly solves all the render stutter due to the baking


    private final RawDownloadStream textureDownStream = new RawDownloadStream(8*1024*1024);//8mb downstream
    private final ModelStore storage = new ModelStore();
    public final ModelFactory factory;
    private final IntLinkedOpenHashSet blockIdQueue = new IntLinkedOpenHashSet();

    private static final GlFramebuffer TMP = new GlFramebuffer();

    public ModelBakerySubsystem(Mapper mapper) {
        this.factory = new ModelFactory(mapper, this.storage, this.textureDownStream);
        TMP.bind(GL_COLOR_ATTACHMENT0, this.storage.textures).verify();
    }

    public void tick() {
        //There should be a method to access the frame time IIRC, if the user framecap is unlimited lock it to like 60 fps for computation
        int BUDGET = 10;//TODO: make this computed based on the remaining free time in a frame (and like div by 2 to reduce overhead) (with a min of 1)

        for (int i = 0; i < BUDGET && !this.blockIdQueue.isEmpty(); i++) {
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
        }

        //Submit is effectively free if nothing is submitted
        this.textureDownStream.submit();

        //Tick the download stream
        this.textureDownStream.tick();


        //Debug blit texture
        glBlitNamedFramebuffer(TMP.id, GL11.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),0,0,256*3*16,256*2*16, 0,0, 256*3*16,256*2*16, GL_COLOR_BUFFER_BIT, GL_NEAREST);
    }

    public void shutdown() {
        this.factory.free();
        this.storage.free();
        this.textureDownStream.free();
    }

    public void requestBlockBake(int blockId) {
        synchronized (this.blockIdQueue) {
            if (this.blockIdQueue.add(blockId)) {
                VarHandle.fullFence();//Ensure memory coherancy
            }
        }
    }

    public void addDebugData(List<String> debug) {
        debug.add("MQ/IF/MC: " + this.blockIdQueue.size() + "/" + this.factory.getInflightCount() + "/" + this.factory.getBakedCount());//Model bake queue/in flight/model baked count
    }
}
