package me.cortex.voxy.client.core.rendering.section;


import me.cortex.voxy.client.RenderStatistics;
import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.GlTexture;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.model.ModelStore;
import me.cortex.voxy.client.core.rendering.section.geometry.BasicSectionGeometryData;
import me.cortex.voxy.client.core.rendering.util.LightMapHelper;
import me.cortex.voxy.client.core.rendering.RenderService;
import me.cortex.voxy.client.core.rendering.util.SharedIndexBuffer;
import me.cortex.voxy.client.core.rendering.util.DownloadStream;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.world.WorldEngine;
import org.joml.Matrix4f;
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

//Uses MDIC to render the sections
public class MDICSectionRenderer extends AbstractSectionRenderer<MDICViewport, BasicSectionGeometryData> {
    private static final int TRANSLUCENT_OFFSET = 400_000;//in draw calls
    private static final int TEMPORAL_OFFSET = 500_000;//in draw calls
    private static final int STATISTICS_BUFFER_BINDING = 7;
    private final Shader terrainShader = Shader.make()
            .defineIf("DEBUG_RENDER", false)
            .add(ShaderType.VERTEX, "voxy:lod/gl46/quads2.vert")
            .add(ShaderType.FRAGMENT, "voxy:lod/gl46/quads.frag")
            .compile();

    private final Shader commandGenShader = Shader.make()
            .define("TRANSLUCENT_OFFSET", TRANSLUCENT_OFFSET)
            .define("TEMPORAL_OFFSET", TEMPORAL_OFFSET)

            .defineIf("HAS_STATISTICS", RenderStatistics.enabled)
            .defineIf("STATISTICS_BUFFER_BINDING", RenderStatistics.enabled, STATISTICS_BUFFER_BINDING)

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
    private final GlBuffer drawCallBuffer = new GlBuffer(5*4*(400_000+100_000+100_000)).zero();//400k draw calls
    private final GlBuffer positionScratchBuffer  = new GlBuffer(8*400000).zero();//400k positions

    //Statistics
    private final GlBuffer statisticsBuffer = new GlBuffer(1024).zero();

    public MDICSectionRenderer(ModelStore modelStore, BasicSectionGeometryData geometryData) {
        super(modelStore, geometryData);
    }

    private void uploadUniformBuffer(MDICViewport viewport) {
        long ptr = UploadStream.INSTANCE.upload(this.uniform, 0, 1024);
        
        var mat = new Matrix4f(viewport.MVP);
        mat.translate(-viewport.innerTranslation.x, -viewport.innerTranslation.y, -viewport.innerTranslation.z);
        mat.getToAddress(ptr); ptr += 4*4*4;

        viewport.section.getToAddress(ptr); ptr += 4*3;

        if (viewport.frameId<0) {
            Logger.error("Frame ID negative, this will cause things to break, wrapping around");
            viewport.frameId &= 0x7fffffff;
        }
        MemoryUtil.memPutInt(ptr, viewport.frameId&0x7fffffff); ptr += 4;
        viewport.innerTranslation.getToAddress(ptr); ptr += 4*3;

        UploadStream.INSTANCE.commit();
    }


    private void bindRenderingBuffers(GlTexture depthBoundTexture) {
        glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniform.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, this.geometryManager.getGeometryBuffer().id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, this.geometryManager.getMetadataBuffer().id);
        this.modelStore.bind(3, 4, 0);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 5, this.positionScratchBuffer.id);
        LightMapHelper.bind(1);
        glBindTextureUnit(2, depthBoundTexture.id);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, SharedIndexBuffer.INSTANCE.id());
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, this.drawCallBuffer.id);
        glBindBuffer(GL_PARAMETER_BUFFER_ARB, this.drawCountCallBuffer.id);
    }

    private void renderTerrain(GlTexture depthBoundTexture, long indirectOffset, long drawCountOffset, int maxDrawCount) {
        //RenderLayer.getCutoutMipped().startDrawing();

        glDisable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
        this.terrainShader.bind();
        glBindVertexArray(RenderService.STATIC_VAO);//Needs to be before binding
        this.bindRenderingBuffers(depthBoundTexture);

        glMemoryBarrier(GL_COMMAND_BARRIER_BIT|GL_SHADER_STORAGE_BARRIER_BIT);//Barrier everything is needed
        glMultiDrawElementsIndirectCountARB(GL_TRIANGLES, GL_UNSIGNED_SHORT, indirectOffset, drawCountOffset, maxDrawCount, 0);

        glEnable(GL_CULL_FACE);
        glBindVertexArray(0);
        glBindSampler(0, 0);
        glBindTextureUnit(0, 0);
        glBindSampler(1, 0);
        glBindTextureUnit(1, 0);

        //RenderLayer.getCutoutMipped().endDrawing();
    }

    @Override
    public void renderOpaque(MDICViewport viewport, GlTexture dbt) {
        if (this.geometryManager.getSectionCount() == 0) return;

        this.uploadUniformBuffer(viewport);

        this.renderTerrain(dbt, 0, 4*3, Math.min((int)(this.geometryManager.getSectionCount()*4.4+128), 400_000));
    }

    @Override
    public void renderTranslucent(MDICViewport viewport, GlTexture depthBoundTexture) {
        if (this.geometryManager.getSectionCount() == 0) return;
        glEnable(GL_BLEND);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

        glDisable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
        this.terrainShader.bind();
        glBindVertexArray(RenderService.STATIC_VAO);//Needs to be before binding
        this.bindRenderingBuffers(depthBoundTexture);

        glMultiDrawElementsIndirectCountARB(GL_TRIANGLES, GL_UNSIGNED_SHORT, TRANSLUCENT_OFFSET*5*4, 4*4, Math.min(this.geometryManager.getSectionCount(), 100_000), 0);

        glEnable(GL_CULL_FACE);
        glBindVertexArray(0);
        glBindSampler(0, 0);
        glBindTextureUnit(0, 0);
        glBindSampler(1, 0);
        glBindTextureUnit(1, 0);

        glDisable(GL_BLEND);
    }

    @Override
    public void buildDrawCalls(MDICViewport viewport) {
        if (this.geometryManager.getSectionCount() == 0) return;
        this.uploadUniformBuffer(viewport);
        //Can do a sneeky trick, since the sectionRenderList is a list to things to render, it invokes the culler
        // which only marks visible sections


        {//Dispatch prep
            this.prepShader.bind();
            glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniform.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, this.drawCountCallBuffer.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, viewport.getRenderList().id);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
            glDispatchCompute(1,1,1);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        }

        {//Test occlusion
            this.cullShader.bind();
            glBindVertexArray(RenderService.STATIC_VAO);
            glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniform.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, this.geometryManager.getMetadataBuffer().id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, viewport.visibilityBuffer.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, viewport.indirectLookupBuffer.id);
            glBindBuffer(GL_DRAW_INDIRECT_BUFFER, this.drawCountCallBuffer.id);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, SharedIndexBuffer.INSTANCE.id());
            glEnable(GL_DEPTH_TEST);
            glColorMask(false, false, false, false);
            glDepthMask(false);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT|GL_COMMAND_BARRIER_BIT);
            glDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_BYTE, 6*4);
            glDepthMask(true);
            glColorMask(true, true, true, true);
            glDisable(GL_DEPTH_TEST);
        }


        {//Generate the commands
            this.commandGenShader.bind();
            glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniform.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, this.drawCallBuffer.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, this.drawCountCallBuffer.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, this.geometryManager.getMetadataBuffer().id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, viewport.visibilityBuffer.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 5, viewport.indirectLookupBuffer.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 6, this.positionScratchBuffer.id);

            if (RenderStatistics.enabled) {
                this.statisticsBuffer.zero();
                glBindBufferBase(GL_SHADER_STORAGE_BUFFER, STATISTICS_BUFFER_BINDING, this.statisticsBuffer.id);
            }

            glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, this.drawCountCallBuffer.id);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
            glDispatchComputeIndirect(0);
            glMemoryBarrier(GL_COMMAND_BARRIER_BIT|GL_SHADER_STORAGE_BARRIER_BIT);

            if (RenderStatistics.enabled) {
                DownloadStream.INSTANCE.download(this.statisticsBuffer, down->{
                    final int LAYERS = WorldEngine.MAX_LOD_LAYER+1;
                    for (int i = 0; i < LAYERS; i++) {
                        RenderStatistics.visibleSections[i] = MemoryUtil.memGetInt(down.address+i*4L);
                    }

                    for (int i = 0; i < LAYERS; i++) {
                        RenderStatistics.quadCount[i] = MemoryUtil.memGetInt(down.address+LAYERS*4L+i*4L);
                    }
                });
            }
        }

    }

    @Override
    public void renderTemporal(GlTexture dbt) {
        if (this.geometryManager.getSectionCount() == 0) return;
        //Render temporal
        this.renderTerrain(dbt, TEMPORAL_OFFSET*5*4, 4*5, Math.min(this.geometryManager.getSectionCount(), 100_000));
    }

    @Override
    public void addDebug(List<String> lines) {
        super.addDebug(lines);
        //lines.add("SC/GS: " + this.geometryManager.getSectionCount() + "/" + (this.geometryManager.getGeometryUsed()/(1024*1024)));//section count/geometry size (MB)
    }

    @Override
    public MDICViewport createViewport() {
        return new MDICViewport(this.geometryManager.getMaxSectionCount());
    }

    @Override
    public void free() {
        this.uniform.free();
        this.terrainShader.free();
        this.commandGenShader.free();
        this.cullShader.free();
        this.prepShader.free();
        this.drawCallBuffer.free();
        this.drawCountCallBuffer.free();
        this.positionScratchBuffer.free();
        this.statisticsBuffer.free();
    }
}
