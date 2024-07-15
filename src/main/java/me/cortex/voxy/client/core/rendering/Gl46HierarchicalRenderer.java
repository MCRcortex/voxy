package me.cortex.voxy.client.core.rendering;

import me.cortex.voxy.client.core.AbstractRenderWorldInteractor;
import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.shader.PrintfInjector;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.model.ModelManager;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.building.RenderDataFactory;
import me.cortex.voxy.client.core.rendering.building.RenderGenerationService;
import me.cortex.voxy.client.core.rendering.hierarchical.HierarchicalOcclusionRenderer;
import me.cortex.voxy.client.core.rendering.hierarchical.INodeInteractor;
import me.cortex.voxy.client.core.rendering.hierarchical.MeshManager;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.client.mixin.joml.AccessFrustumIntersection;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderLayer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.util.List;
import java.util.function.Consumer;

import static org.lwjgl.opengl.ARBDirectStateAccess.glTextureParameteri;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.GL_RED_INTEGER;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL31.glDrawElementsInstanced;
import static org.lwjgl.opengl.GL33.glBindSampler;
import static org.lwjgl.opengl.GL40.glDrawElementsIndirect;
import static org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL42.GL_FRAMEBUFFER_BARRIER_BIT;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.opengl.GL45.glBindTextureUnit;
import static org.lwjgl.opengl.GL45.nglClearNamedBufferSubData;

public class Gl46HierarchicalRenderer implements IRenderInterface<Gl46HierarchicalViewport>, AbstractRenderWorldInteractor {
    private final HierarchicalOcclusionRenderer sectionSelector;
    private final MeshManager meshManager = new MeshManager();
    private final PrintfInjector printf = new PrintfInjector(100000, 10, System.out::println);
    private final GlBuffer renderSections = new GlBuffer(100_000 * 4 + 4).zero();


    private final ModelManager modelManager;
    private RenderGenerationService sectionGenerationService;
    private Consumer<BuiltSection> resultConsumer;

    public Gl46HierarchicalRenderer(ModelManager model) {
        this.modelManager = model;

        this.sectionSelector = new HierarchicalOcclusionRenderer(new INodeInteractor() {
            @Override
            public void watchUpdates(long pos) {

            }

            @Override
            public void unwatchUpdates(long pos) {

            }

            @Override
            public void requestMesh(long pos) {

            }

            @Override
            public void setMeshUpdateCallback(Consumer<BuiltSection> mesh) {
                Gl46HierarchicalRenderer.this.resultConsumer = mesh;
            }
        }, this.meshManager, this.printf);
    }

    @Override
    public void setupRender(Frustum frustum, Camera camera) {

    }

    @Override
    public void renderFarAwayOpaque(Gl46HierarchicalViewport viewport) {
        //Render terrain from previous frame (renderSections)



        {//Run the hierarchical selector over the buffer to generate the set of render sections
            var i = new int[1];
            glGetFramebufferAttachmentParameteriv(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME, i);
            this.sectionSelector.doHierarchicalTraversalSelection(viewport, i[0], this.renderSections);
        }


        this.printf.download();
    }

    @Override
    public void renderFarAwayTranslucent(Gl46HierarchicalViewport viewport) {

    }

    @Override
    public void addDebugData(List<String> debug) {

    }







    @Override
    public void addBlockState(Mapper.StateEntry stateEntry) {

    }

    @Override
    public void addBiome(Mapper.BiomeEntry biomeEntry) {

    }




    @Override
    public void processBuildResult(BuiltSection section) {

    }

    @Override
    public void sectionUpdated(WorldSection worldSection) {

    }





    @Override
    public void initPosition(int x, int z) {

    }

    @Override
    public void setCenter(int x, int y, int z) {

    }






    @Override
    public boolean generateMeshlets() {
        return false;
    }

    @Override
    public void setRenderGen(RenderGenerationService renderService) {
        this.sectionGenerationService = renderService;
    }

    @Override
    public Gl46HierarchicalViewport createViewport() {
        return new Gl46HierarchicalViewport(this);
    }




    @Override
    public void shutdown() {
        this.meshManager.free();
        this.sectionSelector.free();
        this.printf.free();
    }
}
