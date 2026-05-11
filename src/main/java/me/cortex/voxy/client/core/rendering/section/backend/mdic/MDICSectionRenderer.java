package me.cortex.voxy.client.core.rendering.section.backend.mdic;


import me.cortex.voxy.client.RenderStatistics;
import me.cortex.voxy.client.VoxyClient;
import me.cortex.voxy.client.core.AbstractRenderPipeline;
import me.cortex.voxy.client.core.gl.Capabilities;
import me.cortex.voxy.client.core.gpu.IGpuBuffer;
import me.cortex.voxy.client.core.gpu.RenderBackendFactory;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderLoader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.model.ModelStore;
import me.cortex.voxy.client.core.rendering.section.backend.AbstractSectionRenderer;
import me.cortex.voxy.client.core.rendering.section.geometry.BasicSectionGeometryData;
import me.cortex.voxy.client.core.rendering.util.DownloadStream;
import me.cortex.voxy.client.core.rendering.util.LightMapHelper;
import me.cortex.voxy.client.core.rendering.util.SharedIndexBuffer;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.util.List;

import static org.lwjgl.opengl.ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB;
import static org.lwjgl.opengl.ARBIndirectParameters.glMultiDrawElementsIndirectCountARB;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11C.GL_RGBA8;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL15C.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL15C.glGetBufferSubData;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL31C.GL_COPY_READ_BUFFER;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL33.glBindSampler;
import static org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.opengl.GL43.glMultiDrawElementsIndirect;
import static me.cortex.voxy.client.core.gl.GLCompat.bindTextureUnit;
import static org.lwjgl.opengl.NVRepresentativeFragmentTest.GL_REPRESENTATIVE_FRAGMENT_TEST_NV;

//Uses MDIC to render the sections
public class MDICSectionRenderer extends AbstractSectionRenderer<MDICViewport, BasicSectionGeometryData> {
    public static final Factory<MDICViewport, BasicSectionGeometryData> FACTORY = AbstractSectionRenderer.Factory.create(MDICSectionRenderer.class);

    private static final int TRANSLUCENT_OFFSET = 400_000;//in draw calls
    private static final int TEMPORAL_OFFSET = 500_000;//in draw calls
    private static final int STATISTICS_BUFFER_BINDING = 8;
    private final Shader terrainShader;
    private final Shader translucentTerrainShader;

    // M9 migration: MDIC's 5 non-Iris-patched shaders (4 compute + 1 graphics)
    // now flow through RenderBackend.create*Pipeline so they compile cleanly on
    // Metal/Vulkan. terrainShader + translucentTerrainShader stay on the legacy
    // Shader.Builder path because they thread Iris's patchOpaqueShader /
    // patchTranslucentShader callbacks; that path is GL-only after the
    // RenderPipelineFactory gate (commit d9627907). Bind/draw stays raw GL —
    // MDIC operates inside AbstractRenderPipeline's FBO context, not a
    // RenderEncoder.

    private final me.cortex.voxy.client.core.gpu.RenderBackend backend = RenderBackendFactory.get();

    private final me.cortex.voxy.client.core.gpu.IGpuPipeline commandGenPipeline = this.backend.createComputePipeline(
            new me.cortex.voxy.client.core.gpu.ComputePipelineDesc(
                    ShaderLoader.parse("voxy:lod/gl46/cmdgen.comp"),
                    cmdgenDefines(),
                    null, null,
                    32, 1, 1,
                    "MDICSectionRenderer.cmdgen"));
    private final int commandGenProgram = mdicProgramId(this.commandGenPipeline);

    private final me.cortex.voxy.client.core.gpu.IGpuPipeline prepPipeline = this.backend.createComputePipeline(
            new me.cortex.voxy.client.core.gpu.ComputePipelineDesc(
                    ShaderLoader.parse("voxy:lod/gl46/prep.comp"),
                    java.util.Map.of(),
                    null, null,
                    1, 1, 1,
                    "MDICSectionRenderer.prep"));
    private final int prepProgram = mdicProgramId(this.prepPipeline);

    private final me.cortex.voxy.client.core.gpu.IGpuPipeline cullPipeline = this.backend.createGraphicsPipeline(
            new me.cortex.voxy.client.core.gpu.GraphicsPipelineDesc(
                    ShaderLoader.parse("voxy:lod/gl46/cull/raster.vert"),
                    ShaderLoader.parse("voxy:lod/gl46/cull/raster.frag"),
                    java.util.Map.of(),
                    null, null, null, null,
                    GL_RGBA8,
                    me.cortex.voxy.client.core.gpu.VertexLayout.EMPTY,
                    me.cortex.voxy.client.core.gpu.PipelineState.DEFAULT,
                    "MDICSectionRenderer.cull"));
    private final int cullProgram = mdicProgramId(this.cullPipeline);

    private final me.cortex.voxy.client.core.gpu.IGpuPipeline prefixSumPipeline = this.backend.createComputePipeline(
            new me.cortex.voxy.client.core.gpu.ComputePipelineDesc(
                    ShaderLoader.parse(Capabilities.INSTANCE.subgroup ? "voxy:util/prefixsum/inital3.comp" : "voxy:util/prefixsum/simple.comp"),
                    java.util.Map.of("IO_BUFFER", "0"),
                    null, null,
                    32, 1, 1,
                    "MDICSectionRenderer.prefixSum"));
    private final int prefixSumProgram = mdicProgramId(this.prefixSumPipeline);

    private final me.cortex.voxy.client.core.gpu.IGpuPipeline translucentGenPipeline = this.backend.createComputePipeline(
            new me.cortex.voxy.client.core.gpu.ComputePipelineDesc(
                    ShaderLoader.parse("voxy:lod/gl46/buildtranslucents.comp"),
                    java.util.Map.of(
                            "TRANSLUCENT_WRITE_BASE", "1024",
                            "TRANSLUCENT_DISTANCE_BUFFER_BINDING", "5",
                            "TRANSLUCENT_OFFSET", Integer.toString(TRANSLUCENT_OFFSET)),
                    null, null,
                    32, 1, 1,
                    "MDICSectionRenderer.translucentGen"));
    private final int translucentGenProgram = mdicProgramId(this.translucentGenPipeline);

    private static java.util.Map<String, String> cmdgenDefines() {
        var m = new java.util.LinkedHashMap<String, String>();
        m.put("TRANSLUCENT_WRITE_BASE", "1024");
        m.put("TEMPORAL_OFFSET", Integer.toString(TEMPORAL_OFFSET));
        m.put("TRANSLUCENT_DISTANCE_BUFFER_BINDING", "7");
        if (RenderStatistics.enabled) {
            m.put("HAS_STATISTICS", "");
            m.put("STATISTICS_BUFFER_BINDING", Integer.toString(STATISTICS_BUFFER_BINDING));
        }
        return m;
    }

    private static int mdicProgramId(me.cortex.voxy.client.core.gpu.IGpuPipeline p) {
        if (p instanceof me.cortex.voxy.client.core.gl.GlGraphicsPipeline gg) return gg.program();
        if (p instanceof me.cortex.voxy.client.core.gl.GlComputePipeline gc) return gc.program();
        return 0;
    }

    private final IGpuBuffer uniform = RenderBackendFactory.get().createBuffer(1024).zero();//TODO move to viewport?

    //TODO: needs to be in the viewport, since it contains the compute indirect call/values
    private final IGpuBuffer distanceCountBuffer = RenderBackendFactory.get().createBuffer(1024*4+100_000*4).zero();//TODO move to viewport?

    //Statistics
    private final IGpuBuffer statisticsBuffer = RenderBackendFactory.get().createBuffer(1024).zero();

    private final AbstractRenderPipeline pipeline;
    public MDICSectionRenderer(AbstractRenderPipeline pipeline, ModelStore modelStore, BasicSectionGeometryData geometryData) {
        super(modelStore, geometryData);
        this.pipeline = pipeline;
        //The pipeline can be used to transform the renderer in abstract ways

        String vertex = ShaderLoader.parse("voxy:lod/gl46/quads3.vert");
        String taa = pipeline.taaFunction("taaShift");
        if (taa != null) {
            vertex += "\n"+taa;//inject it at the end
        }
        var builder = Shader.make()
                .defineIf("TAA_PATCH", taa != null)
                .defineIf("DEBUG_RENDER", false)

                //.defineIf("USE_NV_BARRY", Capabilities.INSTANCE.nvBarryCoords)

                .addSource(ShaderType.VERTEX, vertex);

        //Apply per face tinting
        addDirectionalFaceTint(builder, Minecraft.getInstance().level);

        String frag = ShaderLoader.parse("voxy:lod/gl46/quads.frag");

        String opaqueFrag = pipeline.patchOpaqueShader(this, frag);
        opaqueFrag = opaqueFrag==null?frag:opaqueFrag;

        //TODO: find a more robust/nicer way todo this
        this.terrainShader = tryCompilePatchedOrNormal(builder, opaqueFrag, frag);

        String translucentFrag = pipeline.patchTranslucentShader(this, frag);
        translucentFrag = translucentFrag==null?frag:translucentFrag;

        this.translucentTerrainShader = tryCompilePatchedOrNormal(builder.define("TRANSLUCENT"), translucentFrag, frag);
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


    private void bindRenderingBuffers(MDICViewport viewport) {
        glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniform.id());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, this.geometryManager.getGeometryBuffer().id());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, this.geometryManager.getMetadataBuffer().id());
        this.modelStore.bind(3, 4, 0);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 5, viewport.positionScratchBuffer.id());
        LightMapHelper.bind(1);
        bindTextureUnit(2, GL_TEXTURE_2D, viewport.depthBoundingBuffer.getDepthTex().id());

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, SharedIndexBuffer.INSTANCE.id());
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, viewport.drawCallBuffer.id());
        glBindBuffer(GL_PARAMETER_BUFFER_ARB, viewport.drawCountCallBuffer.id());
    }

    private void renderTerrain(MDICViewport viewport, long indirectOffset, long drawCountOffset, int maxDrawCount) {
        //RenderLayer.getCutoutMipped().startDrawing();


        glDisable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
        this.terrainShader.bind();
        glBindVertexArray(RenderBackendFactory.get().getStaticVAO());//Needs to be before binding
        this.pipeline.setupAndBindOpaque(viewport);
        this.bindRenderingBuffers(viewport);

        glMemoryBarrier(GL_COMMAND_BARRIER_BIT|GL_SHADER_STORAGE_BARRIER_BIT);//Barrier everything is needed
        glProvokingVertex(GL_FIRST_VERTEX_CONVENTION);

        if (VoxyClient.getOcclusionDebugState()==3) {
            glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
        }
        if (Capabilities.INSTANCE.indirectCount) {
            glMultiDrawElementsIndirectCountARB(GL_TRIANGLES, GL_UNSIGNED_SHORT, indirectOffset, drawCountOffset, maxDrawCount, 0);
        } else {
            int drawCount = Math.min(readDrawCount(viewport.drawCountCallBuffer.id(), drawCountOffset), maxDrawCount);
            glMultiDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_SHORT, indirectOffset, drawCount, 0);
        }
        if (VoxyClient.getOcclusionDebugState()==3) {
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        }

        glEnable(GL_CULL_FACE);
        glBindVertexArray(0);
        glBindSampler(0, 0);
        bindTextureUnit(0, GL_TEXTURE_2D, 0);
        glBindSampler(1, 0);
        bindTextureUnit(1, GL_TEXTURE_2D, 0);

        //RenderLayer.getCutoutMipped().endDrawing();
    }

    @Override
    public void renderOpaque(MDICViewport viewport) {
        if (this.geometryManager.getSectionCount() == 0) return;

        this.uploadUniformBuffer(viewport);

        this.renderTerrain(viewport, 0, 4*3, Math.min((int)(this.geometryManager.getSectionCount()*4.4+128), 400_000));
    }

    @Override
    public void renderTranslucent(MDICViewport viewport) {
        if (this.geometryManager.getSectionCount() == 0) return;

        glEnable(GL_BLEND);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

        glDisable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
        this.translucentTerrainShader.bind();
        glBindVertexArray(RenderBackendFactory.get().getStaticVAO());//Needs to be before binding
        this.pipeline.setupAndBindTranslucent(viewport);
        this.bindRenderingBuffers(viewport);

        glMemoryBarrier(GL_COMMAND_BARRIER_BIT|GL_SHADER_STORAGE_BARRIER_BIT);//Barrier everything is needed
        glProvokingVertex(GL_FIRST_VERTEX_CONVENTION);
        int translucentMax = Math.min(this.geometryManager.getSectionCount(), 100_000);
        if (Capabilities.INSTANCE.indirectCount) {
            glMultiDrawElementsIndirectCountARB(GL_TRIANGLES, GL_UNSIGNED_SHORT, TRANSLUCENT_OFFSET*5*4, 4*4, translucentMax, 0);
        } else {
            int drawCount = Math.min(readDrawCount(viewport.drawCountCallBuffer.id(), 4*4L), translucentMax);
            glMultiDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_SHORT, TRANSLUCENT_OFFSET*5*4, drawCount, 0);
        }

        glEnable(GL_CULL_FACE);
        glBindVertexArray(0);
        glBindSampler(0, 0);
        bindTextureUnit(0, GL_TEXTURE_2D, 0);
        glBindSampler(1, 0);
        bindTextureUnit(1, GL_TEXTURE_2D, 0);

        glDisable(GL_BLEND);
    }

    @Override
    public void buildDrawCalls(MDICViewport viewport) {
        if (this.geometryManager.getSectionCount() == 0) return;
        this.uploadUniformBuffer(viewport);
        //Can do a sneeky trick, since the sectionRenderList is a list to things to render, it invokes the culler
        // which only marks visible sections


        {//Dispatch prep
            if (this.prepProgram != 0) org.lwjgl.opengl.GL20C.glUseProgram(this.prepProgram);
            glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniform.id());
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, viewport.drawCountCallBuffer.id());
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, viewport.getRenderList().id());
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
            glDispatchCompute(1,1,1);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        }

        {//Test occlusion
            if (this.cullProgram != 0) org.lwjgl.opengl.GL20C.glUseProgram(this.cullProgram);
            if (Capabilities.INSTANCE.repFragTest) {
                glEnable(GL_REPRESENTATIVE_FRAGMENT_TEST_NV);
            }
            glBindVertexArray(RenderBackendFactory.get().getStaticVAO());
            glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniform.id());
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, this.geometryManager.getMetadataBuffer().id());
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, viewport.visibilityBuffer.id());
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, viewport.indirectLookupBuffer.id());
            glBindBuffer(GL_DRAW_INDIRECT_BUFFER, viewport.drawCountCallBuffer.id());
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, SharedIndexBuffer.INSTANCE.id());
            glEnable(GL_DEPTH_TEST);
            glColorMask(false, false, false, false);
            glDepthMask(false);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT|GL_COMMAND_BARRIER_BIT);
            glDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_BYTE, 6*4);
            glDepthMask(true);
            glColorMask(true, true, true, true);
            glDisable(GL_DEPTH_TEST);
            if (Capabilities.INSTANCE.repFragTest) {
                glDisable(GL_REPRESENTATIVE_FRAGMENT_TEST_NV);
            }
        }


        {//Generate the commands
            this.distanceCountBuffer.zeroRange(0, 1024*4);
            if (this.commandGenProgram != 0) org.lwjgl.opengl.GL20C.glUseProgram(this.commandGenProgram);
            glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniform.id());
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, viewport.drawCallBuffer.id());
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, viewport.drawCountCallBuffer.id());
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, this.geometryManager.getMetadataBuffer().id());
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, viewport.visibilityBuffer.id());
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 5, viewport.indirectLookupBuffer.id());
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 6, viewport.positionScratchBuffer.id());
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 7, this.distanceCountBuffer.id());

            if (RenderStatistics.enabled) {
                this.statisticsBuffer.zero();
                glBindBufferBase(GL_SHADER_STORAGE_BUFFER, STATISTICS_BUFFER_BINDING, this.statisticsBuffer.id());
            }

            glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, viewport.drawCountCallBuffer.id());
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

        {//Do translucency sorting
            if (this.prefixSumProgram != 0) org.lwjgl.opengl.GL20C.glUseProgram(this.prefixSumProgram);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, this.distanceCountBuffer.id());
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);//Am unsure if is needed
            glDispatchCompute(1,1,1);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

            if (this.translucentGenProgram != 0) org.lwjgl.opengl.GL20C.glUseProgram(this.translucentGenProgram);
            glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniform.id());
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, viewport.drawCallBuffer.id());
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, viewport.drawCountCallBuffer.id());
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, this.geometryManager.getMetadataBuffer().id());
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, viewport.indirectLookupBuffer.id());
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 5, this.distanceCountBuffer.id());

            glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, viewport.drawCountCallBuffer.id());//This isnt great but its a nice trick to bound it, even if its inefficent ;-;
            glMemoryBarrier(GL_COMMAND_BARRIER_BIT|GL_SHADER_STORAGE_BARRIER_BIT|GL_UNIFORM_BARRIER_BIT);
            glDispatchComputeIndirect(0);
            glMemoryBarrier(GL_COMMAND_BARRIER_BIT|GL_SHADER_STORAGE_BARRIER_BIT);
        }

    }

    @Override
    public void renderTemporal(MDICViewport viewport) {
        if (this.geometryManager.getSectionCount() == 0) return;
        //Render temporal
        this.renderTerrain(viewport, TEMPORAL_OFFSET*5*4, 4*5, Math.min(this.geometryManager.getSectionCount(), 100_000));
    }

    private int readDrawCount(int bufferId, long offsetBytes) {
        var tmp = MemoryUtil.memAllocInt(1);
        glBindBuffer(GL_COPY_READ_BUFFER, bufferId);
        glGetBufferSubData(GL_COPY_READ_BUFFER, offsetBytes, tmp);
        glBindBuffer(GL_COPY_READ_BUFFER, 0);
        int count = tmp.get(0);
        MemoryUtil.memFree(tmp);
        return Math.max(count, 0);
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
        this.distanceCountBuffer.free();
        this.translucentTerrainShader.free();
        this.terrainShader.free();
        this.commandGenPipeline.close();
        this.cullPipeline.close();
        this.prepPipeline.close();
        this.translucentGenPipeline.close();
        this.prefixSumPipeline.close();
        this.statisticsBuffer.free();
    }
}
