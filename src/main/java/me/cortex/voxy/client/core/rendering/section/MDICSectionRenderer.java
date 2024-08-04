package me.cortex.voxy.client.core.rendering.section;


import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.model.ModelStore;
import me.cortex.voxy.client.core.rendering.LightMapHelper;
import me.cortex.voxy.client.core.rendering.RenderService;
import me.cortex.voxy.client.core.rendering.SharedIndexBuffer;
import me.cortex.voxy.client.core.rendering.geometry.OLD.AbstractFarWorldRenderer;
import me.cortex.voxy.client.core.rendering.geometry.OLD.AbstractGeometryManager;
import me.cortex.voxy.client.core.rendering.geometry.OLD.Gl46HierarchicalViewport;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.client.mixin.joml.AccessFrustumIntersection;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.util.List;

import static org.lwjgl.opengl.ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL32.glDrawElementsInstancedBaseVertex;
import static org.lwjgl.opengl.GL33.glBindSampler;
import static org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL42.glDrawElementsInstancedBaseVertexBaseInstance;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL45.glBindTextureUnit;

//Uses MDIC to render the sections
public class MDICSectionRenderer extends AbstractSectionRenderer<BasicViewport, BasicSectionGeometryManager> {
    private final Shader terrainShader = Shader.make()
            .add(ShaderType.VERTEX, "voxy:lod/gl46/quads2.vert")
            .add(ShaderType.FRAGMENT, "voxy:lod/gl46/quads.frag")
            .compile();
    private final GlBuffer uniform = new GlBuffer(1024).zero();

    public MDICSectionRenderer(ModelStore modelStore, int maxSectionCount, long geometryCapacity) {
        super(modelStore, new BasicSectionGeometryManager(maxSectionCount, geometryCapacity));
    }


    private void uploadUniformBuffer(BasicViewport viewport) {
        long ptr = UploadStream.INSTANCE.upload(this.uniform, 0, 1024);

        int sx = MathHelper.floor(viewport.cameraX)>>5;
        int sy = MathHelper.floor(viewport.cameraY)>>5;
        int sz = MathHelper.floor(viewport.cameraZ)>>5;
        
        var mat = new Matrix4f(viewport.projection).mul(viewport.modelView);
        var innerTranslation = new Vector3f((float) (viewport.cameraX-(sx<<5)), (float) (viewport.cameraY-(sy<<5)), (float) (viewport.cameraZ-(sz<<5)));
        mat.translate(-innerTranslation.x, -innerTranslation.y, -innerTranslation.z);
        mat.getToAddress(ptr); ptr += 4*4*4;
        MemoryUtil.memPutInt(ptr, sx); ptr += 4;
        MemoryUtil.memPutInt(ptr, sy); ptr += 4;
        MemoryUtil.memPutInt(ptr, sz); ptr += 4;
        MemoryUtil.memPutInt(ptr, viewport.frameId++); ptr += 4;
        innerTranslation.getToAddress(ptr); ptr += 4*3;

        UploadStream.INSTANCE.commit();
    }


    private void bindRenderingBuffers() {
        glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniform.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, this.geometryManager.getGeometryBufferId());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, this.geometryManager.getMetadataBufferId());
        this.modelStore.bind(3, 4, 0);
        LightMapHelper.bind(5);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, SharedIndexBuffer.INSTANCE.id());
        //glBindBuffer(GL_DRAW_INDIRECT_BUFFER, this.glCommandBuffer.id);
        //glBindBuffer(GL_PARAMETER_BUFFER_ARB, this.glCommandCountBuffer.id);

    }

    //Prep the terrain draw calls for this frame, also sets up the
    // remaining render pipeline for this frame
    private void prepTerrainCallsAndPrep() {

    }

    private void renderTerrain() {
        RenderLayer.getCutoutMipped().startDrawing();

        glDisable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
        this.terrainShader.bind();
        glBindVertexArray(RenderService.STATIC_VAO);//Needs to be before binding
        this.bindRenderingBuffers();

        glDrawElementsInstancedBaseVertexBaseInstance(GL_TRIANGLES, 1000*6, GL_UNSIGNED_SHORT, 0,1,0,0);


        glEnable(GL_CULL_FACE);


        glBindVertexArray(0);
        glBindSampler(0, 0);
        glBindTextureUnit(0, 0);
        RenderLayer.getCutoutMipped().endDrawing();
    }

    @Override
    public void renderOpaque(BasicViewport viewport) {
        if (this.geometryManager.getSectionCount() == 0) return;
        this.uploadUniformBuffer(viewport);
    }

    @Override
    public void buildDrawCallsAndRenderTemporal(BasicViewport viewport, GlBuffer sectionRenderList) {
        //Can do a sneeky trick, since the sectionRenderList is a list to things to render, it invokes the culler
        // which only marks visible sections



        //Tick the geometry manager to upload all invalidated metadata changes to the gpu
        this.geometryManager.tick();


        this.renderTerrain();
    }

    @Override
    public void renderTranslucent(BasicViewport viewport) {

    }

    @Override
    public void addDebug(List<String> lines) {
        super.addDebug(lines);
        lines.add("NC/GS: " + this.geometryManager.getSectionCount() + "/" + (this.geometryManager.getGeometryUsed()/(1024*1024)));//Node count/geometry size (MB)
    }

    @Override
    public BasicViewport createViewport() {
        return new BasicViewport();
    }

    @Override
    public void free() {
        super.free();
        this.uniform.free();
        this.terrainShader.free();
    }
}
