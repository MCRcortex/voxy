package me.cortex.voxy.client.core.rendering.geometry.OLD;

import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.shader.PrintfInjector;
import me.cortex.voxy.client.core.model.ModelFactory;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.building.RenderGenerationService;
import me.cortex.voxy.client.core.rendering.hierarchical.DebugRenderer;
import me.cortex.voxy.client.core.rendering.hierarchical.HierarchicalOcclusionRenderer;
import me.cortex.voxy.client.core.rendering.hierarchical.INodeInteractor;
import me.cortex.voxy.client.core.rendering.hierarchical.MeshManager;
import me.cortex.voxy.client.core.rendering.util.DownloadStream;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;

import static org.lwjgl.opengl.GL31.glDrawElementsInstanced;
import static org.lwjgl.opengl.GL40.glDrawElementsIndirect;
import static org.lwjgl.opengl.GL43.*;

public class Gl46HierarchicalRenderer {
    private final HierarchicalOcclusionRenderer sectionSelector;
    private final MeshManager meshManager = new MeshManager();

    private final List<String> printfQueue = new ArrayList<>();
    private final PrintfInjector printf = new PrintfInjector(100000, 10, line->{
        if (line.startsWith("LOG")) {
            System.err.println(line);
        }
        this.printfQueue.add(line);
    }, this.printfQueue::clear);

    private final GlBuffer renderSections = new GlBuffer(100_000 * 4 + 4).zero();
    private final GlBuffer debugNodeQueue = new GlBuffer(1000000*4+4).zero();


    private final DebugRenderer debugRenderer = new DebugRenderer();

    private final ConcurrentLinkedDeque<Mapper.StateEntry> blockStateUpdates = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<Mapper.BiomeEntry> biomeUpdates = new ConcurrentLinkedDeque<>();

    protected final ConcurrentLinkedDeque<BuiltSection> buildResults = new ConcurrentLinkedDeque<>();

    private final ModelFactory modelManager;
    private RenderGenerationService sectionGenerationService;
    private Consumer<BuiltSection> resultConsumer;

    public Gl46HierarchicalRenderer(ModelFactory model) {
        this.modelManager = model;

        this.sectionSelector = new HierarchicalOcclusionRenderer(new INodeInteractor() {
            
            public void watchUpdates(long pos) {
                //System.err.println("Watch: " + pos);
            }

            
            public void unwatchUpdates(long pos) {
                //System.err.println("Unwatch: " + pos);
            }

            
            public void requestMesh(long pos) {
                Gl46HierarchicalRenderer.this.sectionGenerationService.enqueueTask(
                        WorldEngine.getLevel(pos),
                        WorldEngine.getX(pos),
                        WorldEngine.getY(pos),
                        WorldEngine.getZ(pos)
                );
            }

            
            public void setMeshUpdateCallback(Consumer<BuiltSection> mesh) {
                Gl46HierarchicalRenderer.this.resultConsumer = mesh;
            }
        }, this.meshManager, this.printf);
    }

    public void setupRender(Frustum frustum, Camera camera) {
        {//Tick upload and download queues
            UploadStream.INSTANCE.tick();
            DownloadStream.INSTANCE.tick();
        }
    }

    
    public void renderFarAwayOpaque(Gl46HierarchicalViewport viewport) {
        //Process all the build results
        while (!this.buildResults.isEmpty()) {
            this.resultConsumer.accept(this.buildResults.pop());
        }


        //Render terrain from previous frame (renderSections)



        if (true) {//Run the hierarchical selector over the buffer to generate the set of render sections
            var i = new int[1];
            glGetFramebufferAttachmentParameteriv(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME, i);
            this.sectionSelector.doHierarchicalTraversalSelection(viewport, i[0], this.renderSections, this.debugNodeQueue);

            this.debugRenderer.render(viewport, this.sectionSelector.getNodeDataBuffer(), this.debugNodeQueue);
        }


        this.printf.download();
    }

    
    public void renderFarAwayTranslucent(Gl46HierarchicalViewport viewport) {

    }

    
    public void addDebugData(List<String> debug) {
        debug.add("Printf Queue: ");
        debug.addAll(this.printfQueue);
        this.printfQueue.clear();
    }







    
    public void addBlockState(Mapper.StateEntry stateEntry) {
        this.blockStateUpdates.add(stateEntry);
    }

    
    public void addBiome(Mapper.BiomeEntry biomeEntry) {
        this.biomeUpdates.add(biomeEntry);
    }




    public void processBuildResult(BuiltSection section) {
        this.buildResults.add(section);
    }

    public void initPosition(int X, int Z) {
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                for (int y = -1; y <= 0; y++) {
                    long pos = WorldEngine.getWorldSectionId(4, x,y,z);
                    this.sectionSelector.nodeManager.insertTopLevelNode(pos);
                }
            }
        }
    }




    
    public boolean generateMeshlets() {
        return false;
    }

    public void setRenderGen(RenderGenerationService renderService) {
        this.sectionGenerationService = renderService;
    }

    
    public Gl46HierarchicalViewport createViewport() {
        return new Gl46HierarchicalViewport(this);
    }




    
    public void shutdown() {
        this.meshManager.free();
        this.sectionSelector.free();
        this.printf.free();
        this.debugRenderer.free();
    }
}
