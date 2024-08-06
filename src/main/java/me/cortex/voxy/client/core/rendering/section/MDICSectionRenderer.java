package me.cortex.voxy.client.core.rendering.section;


import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.model.ModelStore;
import me.cortex.voxy.client.core.rendering.LightMapHelper;
import me.cortex.voxy.client.core.rendering.RenderService;
import me.cortex.voxy.client.core.rendering.SharedIndexBuffer;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.util.List;

import static org.lwjgl.opengl.ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB;
import static org.lwjgl.opengl.ARBIndirectParameters.glMultiDrawElementsIndirectCountARB;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL33.glBindSampler;
import static org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.opengl.GL45.glBindTextureUnit;
import static org.lwjgl.opengl.GL45.glCopyNamedBufferSubData;

//Uses MDIC to render the sections
public class MDICSectionRenderer extends AbstractSectionRenderer<MDICViewport, BasicSectionGeometryManager> {
    private final Shader terrainShader = Shader.make()
            .add(ShaderType.VERTEX, "voxy:lod/gl46/quads2.vert")
            .add(ShaderType.FRAGMENT, "voxy:lod/gl46/quads.frag")
            .compile();

    private final Shader commandGenShader = Shader.make()
            .add(ShaderType.COMPUTE, "voxy:lod/gl46/cmdgen.comp")
            .compile();

    private final Shader prepShader = Shader.make()
            .add(ShaderType.COMPUTE, "voxy:lod/gl46/prep.comp")
            .compile();

    private final Shader cullShader = Shader.make()
            .add(ShaderType.VERTEX, "voxy:lod/gl46/cull/raster.vert")
            .add(ShaderType.FRAGMENT, "voxy:lod/gl46/cull/raster.frag")
            .compile();

    private final GlBuffer uniform = new GlBuffer(1024).zero();

    //TODO: needs to be in the viewport, since it contains the compute indirect call/values
    private final GlBuffer drawCountCallBuffer = new GlBuffer(1024).zero();
    private final GlBuffer drawCallBuffer  = new GlBuffer(5*4*400000).zero();//400k draw calls

    public MDICSectionRenderer(ModelStore modelStore, int maxSectionCount, long geometryCapacity) {
        super(modelStore, new BasicSectionGeometryManager(maxSectionCount, geometryCapacity));
    }


    private void uploadUniformBuffer(MDICViewport viewport) {
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
        MemoryUtil.memPutInt(ptr, viewport.frameId); ptr += 4;
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
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, this.drawCallBuffer.id);
        glBindBuffer(GL_PARAMETER_BUFFER_ARB, this.drawCountCallBuffer.id);

    }


    private void renderTerrain() {
        RenderLayer.getCutoutMipped().startDrawing();

        glDisable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
        this.terrainShader.bind();
        glBindVertexArray(RenderService.STATIC_VAO);//Needs to be before binding
        this.bindRenderingBuffers();

        glMultiDrawElementsIndirectCountARB(GL_TRIANGLES, GL_UNSIGNED_SHORT, 0, 4*3, Math.min((int)(this.geometryManager.getSectionCount()*4.4+128), 400_000), 0);

        glEnable(GL_CULL_FACE);
        glBindVertexArray(0);
        glBindSampler(0, 0);
        glBindTextureUnit(0, 0);
        RenderLayer.getCutoutMipped().endDrawing();
    }

    @Override
    public void renderOpaque(MDICViewport viewport) {
        if (this.geometryManager.getSectionCount() == 0) return;



        //Tick the geometry manager to upload all invalidated metadata changes to the gpu before invoking the command gen shader
        this.geometryManager.tick();

        this.uploadUniformBuffer(viewport);

        //TODO compute the draw calls
        {
            this.commandGenShader.bind();
            glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniform.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, this.drawCallBuffer.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, this.drawCountCallBuffer.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, this.geometryManager.getMetadataBufferId());
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, viewport.visibilityBuffer.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 5, viewport.indirectLookupBuffer.id);
            glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, this.drawCountCallBuffer.id);
            glDispatchComputeIndirect(0);
            glMemoryBarrier(GL_COMMAND_BARRIER_BIT);
        }

        this.renderTerrain();
    }

    @Override
    public void buildDrawCallsAndRenderTemporal(MDICViewport viewport, GlBuffer sectionRenderList) {
        //Can do a sneeky trick, since the sectionRenderList is a list to things to render, it invokes the culler
        // which only marks visible sections



        //TODO: dont do a copy
        // make it so that the viewport contains the original indirectLookupBuffer list!!!
        // that way dont need to copy the array
        glCopyNamedBufferSubData(sectionRenderList.id, viewport.indirectLookupBuffer.id, 0, 0, sectionRenderList.size());

        {//Dispatch prep
            this.prepShader.bind();
            glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniform.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, this.drawCountCallBuffer.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, sectionRenderList.id);
            glDispatchCompute(1,1,1);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        }

        {//Test occlusion
            this.cullShader.bind();
            glBindVertexArray(RenderService.STATIC_VAO);
            glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniform.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, this.geometryManager.getMetadataBufferId());
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, viewport.visibilityBuffer.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, viewport.indirectLookupBuffer.id);
            glBindBuffer(GL_DRAW_INDIRECT_BUFFER, this.drawCountCallBuffer.id);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, SharedIndexBuffer.INSTANCE.id());
            glEnable(GL_DEPTH_TEST);
            glColorMask(false, false, false, false);
            glDepthMask(false);
            glDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_BYTE, 6*4);
            glDepthMask(true);
            glColorMask(true, true, true, true);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
            glDisable(GL_DEPTH_TEST);
        }

    }

    @Override
    public void renderTranslucent(MDICViewport viewport) {

    }

    @Override
    public void addDebug(List<String> lines) {
        super.addDebug(lines);
        lines.add("NC/GS: " + this.geometryManager.getSectionCount() + "/" + (this.geometryManager.getGeometryUsed()/(1024*1024)));//Node count/geometry size (MB)
    }

    @Override
    public MDICViewport createViewport() {
        return new MDICViewport();
    }

    @Override
    public void free() {
        super.free();
        this.uniform.free();
        this.terrainShader.free();
        this.commandGenShader.free();
        this.cullShader.free();
        this.prepShader.free();
        this.drawCallBuffer.free();
        this.drawCountCallBuffer.free();
    }
}
