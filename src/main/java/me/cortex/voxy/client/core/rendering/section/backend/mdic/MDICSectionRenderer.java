package me.cortex.voxy.client.core.rendering.section.backend.mdic;


import me.cortex.voxy.client.RenderStatistics;
import me.cortex.voxy.client.VoxyClient;
import me.cortex.voxy.client.core.AbstractRenderPipeline;
import me.cortex.voxy.client.core.gl.Capabilities;
import me.cortex.voxy.client.core.gpu.BackendType;
import me.cortex.voxy.client.core.gpu.ComputeEncoder;
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
    /**
     * Terrain shaders. Two paths:
     *   - Iris-patched (legacy {@link Shader.Builder}, GL-only by definition since
     *     the Iris pipeline is GL-gated in RenderPipelineFactory).
     *   - Unpatched ({@link me.cortex.voxy.client.core.gpu.IGpuPipeline} via
     *     createGraphicsPipeline). On Metal this is the only path that ever
     *     runs; on GL it's used when no Iris pack is active.
     * Exactly one of each pair is non-null per opaque/translucent slot.
     */
    private final Shader terrainShader;
    private final Shader translucentTerrainShader;
    private final me.cortex.voxy.client.core.gpu.IGpuPipeline terrainPipeline;
    private final me.cortex.voxy.client.core.gpu.IGpuPipeline translucentTerrainPipeline;
    private final int terrainProgram;
    private final int translucentTerrainProgram;

    // M9 migration: MDIC's 5 non-Iris-patched shaders (4 compute + 1 graphics)
    // now flow through RenderBackend.create*Pipeline so they compile cleanly on
    // Metal/Vulkan. terrainShader + translucentTerrainShader stay on the legacy
    // Shader.Builder path because they thread Iris's patchOpaqueShader /
    // patchTranslucentShader callbacks; that path is GL-only after the
    // RenderPipelineFactory gate (commit 1e2a1190). Bind/draw stays raw GL —
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
    // M12 chunk 3: commandGen prepass is dispatched via ComputeEncoder; no
    // cached glProgram id needed.

    private final me.cortex.voxy.client.core.gpu.IGpuPipeline prepPipeline = this.backend.createComputePipeline(
            new me.cortex.voxy.client.core.gpu.ComputePipelineDesc(
                    ShaderLoader.parse("voxy:lod/gl46/prep.comp"),
                    java.util.Map.of(),
                    null, null,
                    1, 1, 1,
                    "MDICSectionRenderer.prep"));
    // M12 chunk 2: prep prepass is dispatched via ComputeEncoder; no cached
    // glProgram id needed (encoder pulls it from GlComputePipeline on GL,
    // MTLComputePipelineState on Metal).

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

    /**
     * M12 chunk 5 Metal stub: substitutes for the depth-test-based cull pass
     * on backends that can't currently open a depth-only render pass against
     * MC's depth buffer. Writes `visibilityData[sid] = frameId | (1<<31)` for
     * every section in `indirectLookup`, so cmdgen queues all frustum-visible
     * sections for rendering (slower than real depth occlusion but
     * functionally correct). Allocated unconditionally — only dispatched
     * when the backend isn't OpenGL. Negligible memory cost; the alternative
     * (gating allocation behind a backend check) makes the class harder to
     * read for no real benefit.
     */
    private final me.cortex.voxy.client.core.gpu.IGpuPipeline forceAllVisiblePipeline = this.backend.createComputePipeline(
            new me.cortex.voxy.client.core.gpu.ComputePipelineDesc(
                    ShaderLoader.parse("voxy:lod/gl46/force_all_visible.comp"),
                    java.util.Map.of(),
                    null, null,
                    128, 1, 1,
                    "MDICSectionRenderer.forceAllVisible"));

    private final me.cortex.voxy.client.core.gpu.IGpuPipeline prefixSumPipeline = this.backend.createComputePipeline(
            new me.cortex.voxy.client.core.gpu.ComputePipelineDesc(
                    ShaderLoader.parse(Capabilities.INSTANCE.subgroup ? "voxy:util/prefixsum/inital3.comp" : "voxy:util/prefixsum/simple.comp"),
                    java.util.Map.of("IO_BUFFER", "0"),
                    null, null,
                    32, 1, 1,
                    "MDICSectionRenderer.prefixSum"));
    // M12 chunk 1: prefixSum prepass is dispatched via ComputeEncoder, so it
    // does not need a cached glProgram id (the encoder pulls it from the
    // GlComputePipeline directly on GL; Metal uses the MTLComputePipelineState).

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
    // M12 chunk 4: translucentGen prepass is dispatched via ComputeEncoder;
    // no cached glProgram id needed.

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
        boolean opaquePatched = opaqueFrag != null;
        if (!opaquePatched) opaqueFrag = frag;

        String translucentFrag = pipeline.patchTranslucentShader(this, frag);
        boolean translucentPatched = translucentFrag != null;
        if (!translucentPatched) translucentFrag = frag;

        if (opaquePatched || translucentPatched) {
            // Iris-patched path stays on the legacy Shader.Builder. It's GL-only
            // because the Iris pipeline itself is now gated to OpenGL in
            // RenderPipelineFactory (commit 1e2a1190).
            this.terrainShader = tryCompilePatchedOrNormal(builder, opaqueFrag, frag);
            this.translucentTerrainShader = tryCompilePatchedOrNormal(
                    builder.define("TRANSLUCENT"), translucentFrag, frag);
            this.terrainPipeline = null;
            this.translucentTerrainPipeline = null;
            this.terrainProgram = 0;
            this.translucentTerrainProgram = 0;
        } else {
            // Unpatched path — runs on every backend including Metal. Build the
            // two pipelines via the cross-backend abstraction. Defines mirror
            // what Shader.Builder collected above (face-tint floats from
            // addDirectionalFaceTint + TAA_PATCH if a TAA function exists).
            this.terrainShader = null;
            this.translucentTerrainShader = null;
            java.util.Map<String, String> commonDefines = buildTerrainDefines(taa);
            java.util.Map<String, String> opaqueDefines = new java.util.LinkedHashMap<>(commonDefines);
            java.util.Map<String, String> translucentDefines = new java.util.LinkedHashMap<>(commonDefines);
            translucentDefines.put("TRANSLUCENT", "");
            // M12 chunk 6 step 3 follow-up: on non-GL backends the model
            // texture atlas (ModelTextureBakery) and the depth-bounding
            // texture aren't bound yet (their callers stay raw GL — see the
            // M9 file migration order). Inject `VOXY_NO_ATLAS` so quads.frag
            // skips the atlas-driven sampling + alpha discard + depth-bounds
            // check and instead emits a deterministic per-instance debug
            // color. Lets us see Voxy's LOD chunks on Metal as
            // distinct-coloured blocks while the real texture path is still
            // pending.
            if (this.backend.getType() != BackendType.OPENGL) {
                opaqueDefines.put("VOXY_NO_ATLAS", "");
                translucentDefines.put("VOXY_NO_ATLAS", "");
            }

            // NOTE: MDIC terrain pipelines do NOT opt into supportIndirectCommandBuffers.
            // quads.frag uses gl_FragDepth writes + discard, both incompatible
            // with Metal's ICB linking ("Fragment shader cannot be used with
            // indirect command buffers"). For now MDIC uses the CPU-readback
            // path on Metal (read drawCountCallBuffer back, issue per-draw
            // glMultiDrawElementsIndirect — which MetalRenderEncoder.drawIndexedIndirect
            // implements as a CPU loop). The ICB infrastructure stays available
            // (smoke-tested independently) for future simpler-shader use cases.
            //
            // M12 chunk 6 polish: on non-GL backends the pipeline state uses
            // NO_CULL because the GL renderTerrain path explicitly calls
            // glDisable(GL_CULL_FACE) at draw time — that override doesn't
            // apply to Metal where the cull mode is baked into the pipeline.
            // Without this, ~half the LOD triangles disappear due to wrong-
            // winding back-face culling.
            me.cortex.voxy.client.core.gpu.PipelineState opaqueState
                    = me.cortex.voxy.client.core.gpu.PipelineState.OPAQUE_MESH;
            me.cortex.voxy.client.core.gpu.PipelineState translucentState
                    = me.cortex.voxy.client.core.gpu.PipelineState.TRANSLUCENT_MESH;
            if (this.backend.getType() != BackendType.OPENGL) {
                opaqueState = new me.cortex.voxy.client.core.gpu.PipelineState(
                        me.cortex.voxy.client.core.gpu.PipelineState.DepthState.DEFAULT,
                        me.cortex.voxy.client.core.gpu.PipelineState.BlendState.OPAQUE,
                        me.cortex.voxy.client.core.gpu.PipelineState.RasterState.NO_CULL);
                translucentState = new me.cortex.voxy.client.core.gpu.PipelineState(
                        me.cortex.voxy.client.core.gpu.PipelineState.DepthState.TEST_NO_WRITE,
                        me.cortex.voxy.client.core.gpu.PipelineState.BlendState.PREMULTIPLIED_ALPHA,
                        me.cortex.voxy.client.core.gpu.PipelineState.RasterState.NO_CULL);
            }
            this.terrainPipeline = this.backend.createGraphicsPipeline(
                    new me.cortex.voxy.client.core.gpu.GraphicsPipelineDesc(
                            vertex, frag, opaqueDefines,
                            null, null, null, null,
                            GL_RGBA8,
                            me.cortex.voxy.client.core.gpu.VertexLayout.EMPTY,
                            opaqueState,
                            "MDIC.terrain"));
            this.translucentTerrainPipeline = this.backend.createGraphicsPipeline(
                    new me.cortex.voxy.client.core.gpu.GraphicsPipelineDesc(
                            vertex, frag, translucentDefines,
                            null, null, null, null,
                            GL_RGBA8,
                            me.cortex.voxy.client.core.gpu.VertexLayout.EMPTY,
                            translucentState,
                            "MDIC.translucentTerrain"));
            this.terrainProgram = mdicProgramId(this.terrainPipeline);
            this.translucentTerrainProgram = mdicProgramId(this.translucentTerrainPipeline);
        }
    }

    /** Mirror addDirectionalFaceTint + the TAA flag so the cross-backend pipeline desc gets the same defines. */
    private static java.util.Map<String, String> buildTerrainDefines(String taa) {
        var m = new java.util.LinkedHashMap<String, String>();
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            m.put("NO_SHADE_FACE_TINT", Float.toString(level.getShade(Direction.UP, false)) + "f");
            m.put("UP_FACE_TINT",       Float.toString(level.getShade(Direction.UP, true))  + "f");
            m.put("DOWN_FACE_TINT",     Float.toString(level.getShade(Direction.DOWN, true))+ "f");
            m.put("Z_AXIS_FACE_TINT",   Float.toString(level.getShade(Direction.NORTH, true))+ "f");
            m.put("X_AXIS_FACE_TINT",   Float.toString(level.getShade(Direction.EAST, true)) + "f");
        }
        if (taa != null) m.put("TAA_PATCH", "");
        return m;
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
        // SceneUniform is now an SSBO (see bindings.glsl); bind it to the
        // GL_SHADER_STORAGE_BUFFER target so the in-shader binding=0 matches.
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, this.uniform.id());
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
        if (this.terrainShader != null) {
            this.terrainShader.bind();
        } else if (this.terrainProgram != 0) {
            org.lwjgl.opengl.GL20C.glUseProgram(this.terrainProgram);
        }
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

    /**
     * M12 chunk 6 step 3 — Metal-only opaque draw via {@link RenderEncoder}.
     * Called from {@code AbstractRenderPipeline.runPipelineMetal} inside a
     * render pass that targets the IOSurface bridge color + Voxy's
     * Metal-side depth texture.
     *
     * Differences from the GL {@link #renderTerrain}:
     * <ul>
     *   <li>No raw {@code glUseProgram} / {@code glBindBufferBase} / vertex
     *       array binding — all flows through {@link RenderEncoder.setPipeline}
     *       / {@code setBuffer}.</li>
     *   <li>No {@code setupAndBindOpaque} — the render pass already targets
     *       the bridge; there's no separate FBO bind step.</li>
     *   <li>Lightmap (binding 1 sampler) and depth-bounding texture (binding 2
     *       sampler) are NOT bound — both source from MC's GL context and have
     *       no cross-context handle yet. Terrain renders with default-sampled
     *       textures (likely zeros), so lighting/cutout look wrong but
     *       geometry is visible.</li>
     *   <li>Model atlas texture + sampler (binding 0) also skipped —
     *       {@code ModelTextureBakery} is GL-only so the atlas is blank on
     *       Metal anyway; only the model + colour SSBOs feed shape data.</li>
     *   <li>{@code drawIndexedIndirect} with CPU-side {@code maxDrawCount}
     *       instead of {@code drawIndexedIndirectCount} — Metal has no
     *       count-aware MDI compatible with quads.frag (see gotcha #16);
     *       the cmdBuffer zero pass in {@link #buildDrawCalls} makes
     *       beyond-the-count slots no-op.</li>
     * </ul>
     */
    public void renderOpaqueMetal(me.cortex.voxy.client.core.gpu.RenderEncoder encoder, MDICViewport viewport) {
        if (this.geometryManager.getSectionCount() == 0) return;
        // The uniform was already uploaded inside buildDrawCalls; uploading
        // again here would clobber the SceneUniform with a new pointer in the
        // same frame. Only re-upload if the call path skipped buildDrawCalls.
        // Conservative: re-upload — UploadStream coalesces and this matches
        // the GL renderOpaque pattern.
        this.uploadUniformBuffer(viewport);
        if (this.terrainPipeline == null) {
            // Iris-patched path — GL-only by construction (see 1e2a1190). Should
            // never hit on Metal because RenderPipelineFactory gates Iris pipeline.
            return;
        }
        int maxDrawCount = Math.min((int)(this.geometryManager.getSectionCount()*4.4+128), 400_000);
        this.renderTerrainMetal(encoder, this.terrainPipeline, viewport, 0L, maxDrawCount);
    }

    /**
     * M12 Metal-side temporal render — reuses the opaque terrain pipeline but
     * draws from the temporal slice of {@code drawCallBuffer}
     * ({@code TEMPORAL_OFFSET}+ slots, populated by commandGen.comp for sections
     * that were visible-this-frame-but-not-last). On GL the equivalent path
     * is {@link #renderTemporal}, which forwards to {@link #renderTerrain}
     * with the temporal offsets.
     */
    public void renderTemporalMetal(me.cortex.voxy.client.core.gpu.RenderEncoder encoder, MDICViewport viewport) {
        if (this.geometryManager.getSectionCount() == 0) return;
        if (this.terrainPipeline == null) return;
        int maxDrawCount = Math.min(this.geometryManager.getSectionCount(), 100_000);
        this.renderTerrainMetal(encoder, this.terrainPipeline, viewport,
                /*indirectOffset bytes*/ (long) TEMPORAL_OFFSET * 5L * 4L,
                maxDrawCount);
    }

    /**
     * M12 Metal-side translucent render — uses the dedicated translucent
     * pipeline ({@code TRANSLUCENT_MESH}-style state with depth-test-no-write
     * + premultiplied-alpha blend, both baked in at pipeline creation) and
     * draws from the translucent slice of {@code drawCallBuffer}
     * ({@code TRANSLUCENT_OFFSET}+ slots, populated by buildtranslucents.comp).
     * Same SSBO bindings as opaque since both share quads3.vert + quads.frag;
     * blend + depth state come from the pipeline state, no per-draw GL state
     * changes needed.
     */
    public void renderTranslucentMetal(me.cortex.voxy.client.core.gpu.RenderEncoder encoder, MDICViewport viewport) {
        if (this.geometryManager.getSectionCount() == 0) return;
        if (this.translucentTerrainPipeline == null) return;
        int translucentMax = Math.min(this.geometryManager.getSectionCount(), 100_000);
        this.renderTerrainMetal(encoder, this.translucentTerrainPipeline, viewport,
                /*indirectOffset bytes*/ (long) TRANSLUCENT_OFFSET * 5L * 4L,
                translucentMax);
    }

    private void renderTerrainMetal(me.cortex.voxy.client.core.gpu.RenderEncoder encoder,
                                    me.cortex.voxy.client.core.gpu.IGpuPipeline pipeline,
                                    MDICViewport viewport, long indirectOffset, int maxDrawCount) {
        encoder.setPipeline(pipeline);
        // SSBO bindings 0..5 — mirror bindRenderingBuffers; SceneUniform is an
        // SSBO post-chunk-3 SceneUniform flip.
        encoder.setBuffer(0, this.uniform, 0);
        encoder.setBuffer(1, this.geometryManager.getGeometryBuffer(), 0);
        encoder.setBuffer(2, this.geometryManager.getMetadataBuffer(), 0);
        this.modelStore.bindBuffers(encoder, 3, 4);
        encoder.setBuffer(5, viewport.positionScratchBuffer, 0);
        // Texture / sampler bindings 0 (modelAtlas), 1 (lightmap), 2
        // (depthBoundingBuffer) intentionally skipped — see method javadoc.

        encoder.bindIndexBuffer(me.cortex.voxy.client.core.rendering.util.SharedIndexBuffer.INSTANCE.getBuffer(),
                me.cortex.voxy.client.core.gpu.RenderEncoder.INDEX_TYPE_UINT16, 0);
        encoder.drawIndexedIndirect(
                me.cortex.voxy.client.core.gpu.RenderEncoder.PRIMITIVE_TRIANGLES,
                viewport.drawCallBuffer, indirectOffset,
                maxDrawCount,
                /*stride*/ 5 * 4); // DrawElementsIndirectCommand = 5 uint32
    }

    @Override
    public void renderTranslucent(MDICViewport viewport) {
        if (this.geometryManager.getSectionCount() == 0) return;

        glEnable(GL_BLEND);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

        glDisable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
        if (this.translucentTerrainShader != null) {
            this.translucentTerrainShader.bind();
        } else if (this.translucentTerrainProgram != 0) {
            org.lwjgl.opengl.GL20C.glUseProgram(this.translucentTerrainProgram);
        }
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

        // On non-GL backends the renderer issues `drawIndexedIndirect` against
        // viewport.drawCallBuffer with a CPU-side `maxDrawCount` upper bound
        // (Metal has no count-aware MDI without an ICB, and MDIC's terrain
        // pipeline opts out of ICB — see gotcha #16). Zeroing the cmdBuffer
        // first means slots beyond what commandGen fills hold instanceCount=0,
        // so those iterations no-op instead of replaying stale draws from
        // the previous frame.
        if (this.backend.getType() != BackendType.OPENGL) {
            viewport.drawCallBuffer.zero();
        }

        //Can do a sneeky trick, since the sectionRenderList is a list to things to render, it invokes the culler
        // which only marks visible sections


        {//Dispatch prep
            // M12 chunk 2: prep prepass migrated to ComputeEncoder. prep.comp
            // does not reference SceneUniform fields (only writes to the
            // DrawCommandCountBuffer at binding 1 from sectionCount at
            // binding 2), so the encoder skips binding 0 entirely — that
            // keeps the SceneUniform UBO→SSBO decision deferred to chunks
            // 3 and 4 (which DO use SceneUniform).
            try (var encoder = this.backend.beginComputePass()) {
                encoder.setPipeline(this.prepPipeline);
                encoder.setBuffer(1, viewport.drawCountCallBuffer, 0);
                encoder.setBuffer(2, viewport.getRenderList(), 0);
                encoder.barrier(ComputeEncoder.BARRIER_SHADER, ComputeEncoder.BARRIER_SHADER);
                encoder.dispatch(1, 1, 1);
                encoder.barrier(ComputeEncoder.BARRIER_SHADER, ComputeEncoder.BARRIER_SHADER);
            }
        }

        {//Test occlusion
            if (this.backend.getType() == BackendType.OPENGL) {
                // GL path — depth-test-based occlusion cull. Rasterizes each
                // section's AABB against MC's depth buffer with color/depth
                // masks off; raster.frag writes visibilityData for sections
                // whose AABBs survive depth test (with optional
                // NV_representative_fragment_test for perf).
                if (this.cullProgram != 0) org.lwjgl.opengl.GL20C.glUseProgram(this.cullProgram);
                if (Capabilities.INSTANCE.repFragTest) {
                    glEnable(GL_REPRESENTATIVE_FRAGMENT_TEST_NV);
                }
                glBindVertexArray(RenderBackendFactory.get().getStaticVAO());
                // SceneUniform is an SSBO now (see bindings.glsl).
                glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, this.uniform.id());
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
            } else {
                // Non-GL path (Metal today) — compute stub that skips
                // occlusion and marks every frustum-visible section as
                // visible-this-frame + visible-last-frame. Slower than real
                // depth occlusion but functionally correct; the real cull
                // depends on cross-context MC-depth access which is part of
                // chunk 6's IGpuRenderTarget work.
                try (var encoder = this.backend.beginComputePass()) {
                    encoder.setPipeline(this.forceAllVisiblePipeline);
                    encoder.setBuffer(0, this.uniform, 0);
                    encoder.setBuffer(2, viewport.visibilityBuffer, 0);
                    encoder.setBuffer(3, viewport.indirectLookupBuffer, 0);
                    encoder.barrier(ComputeEncoder.BARRIER_SHADER | ComputeEncoder.BARRIER_INDIRECT,
                                    ComputeEncoder.BARRIER_SHADER | ComputeEncoder.BARRIER_INDIRECT);
                    // Reuses prep's dispatch sizing — cmdGenDispatchX/Y/Z at
                    // offset 0 of drawCountCallBuffer holds ceil(sectionCount/128),
                    // matching this shader's local_size_x=128.
                    encoder.dispatchIndirect(viewport.drawCountCallBuffer, 0);
                    encoder.barrier(ComputeEncoder.BARRIER_SHADER, ComputeEncoder.BARRIER_SHADER);
                }
            }
        }


        {//Generate the commands
            this.distanceCountBuffer.zeroRange(0, 1024*4);
            // M12 chunk 3: commandGen migrated to ComputeEncoder. SceneUniform
            // is now an SSBO (see bindings.glsl), so binding 0 flows through
            // setBuffer just like the other SSBOs. Read count comes from
            // drawCountCallBuffer at offset 0 via dispatchIndirect.
            if (RenderStatistics.enabled) {
                this.statisticsBuffer.zero();
            }
            try (var encoder = this.backend.beginComputePass()) {
                encoder.setPipeline(this.commandGenPipeline);
                encoder.setBuffer(0, this.uniform, 0);
                encoder.setBuffer(1, viewport.drawCallBuffer, 0);
                encoder.setBuffer(2, viewport.drawCountCallBuffer, 0);
                encoder.setBuffer(3, this.geometryManager.getMetadataBuffer(), 0);
                encoder.setBuffer(4, viewport.visibilityBuffer, 0);
                encoder.setBuffer(5, viewport.indirectLookupBuffer, 0);
                encoder.setBuffer(6, viewport.positionScratchBuffer, 0);
                encoder.setBuffer(7, this.distanceCountBuffer, 0);
                if (RenderStatistics.enabled) {
                    encoder.setBuffer(STATISTICS_BUFFER_BINDING, this.statisticsBuffer, 0);
                }
                encoder.barrier(ComputeEncoder.BARRIER_SHADER, ComputeEncoder.BARRIER_SHADER);
                encoder.dispatchIndirect(viewport.drawCountCallBuffer, 0);
                encoder.barrier(ComputeEncoder.BARRIER_SHADER | ComputeEncoder.BARRIER_INDIRECT,
                                ComputeEncoder.BARRIER_SHADER | ComputeEncoder.BARRIER_INDIRECT);
            }

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
            // M12 chunk 1: prefixSum migrated to ComputeEncoder. Runs on every
            // backend (GL lowers to glUseProgram + glBindBufferBase +
            // glDispatchCompute; Metal opens an MTLComputeCommandEncoder). The
            // previous raw-GL pattern no-opped on Metal because
            // mdicProgramId(prefixSumPipeline) returns 0.
            try (var encoder = this.backend.beginComputePass()) {
                encoder.setPipeline(this.prefixSumPipeline);
                encoder.setBuffer(0, this.distanceCountBuffer, 0);
                encoder.barrier(ComputeEncoder.BARRIER_SHADER, ComputeEncoder.BARRIER_SHADER);
                encoder.dispatch(1, 1, 1);
                encoder.barrier(ComputeEncoder.BARRIER_SHADER, ComputeEncoder.BARRIER_SHADER);
            }

            // M12 chunk 4: translucentGen migrated to ComputeEncoder. SceneUniform
            // is bound as an SSBO at binding 0 (post-flip in chunk 3); the
            // dispatch count comes from drawCountCallBuffer at offset 0 via
            // dispatchIndirect — the same buffer doubles as the indirect arg
            // and as one of the shader's SSBO inputs at binding 2 (an unusual
            // but pre-existing read-then-dispatch pattern).
            try (var encoder = this.backend.beginComputePass()) {
                encoder.setPipeline(this.translucentGenPipeline);
                encoder.setBuffer(0, this.uniform, 0);
                encoder.setBuffer(1, viewport.drawCallBuffer, 0);
                encoder.setBuffer(2, viewport.drawCountCallBuffer, 0);
                encoder.setBuffer(3, this.geometryManager.getMetadataBuffer(), 0);
                encoder.setBuffer(4, viewport.indirectLookupBuffer, 0);
                encoder.setBuffer(5, this.distanceCountBuffer, 0);
                encoder.barrier(ComputeEncoder.BARRIER_SHADER | ComputeEncoder.BARRIER_INDIRECT,
                                ComputeEncoder.BARRIER_SHADER | ComputeEncoder.BARRIER_INDIRECT);
                encoder.dispatchIndirect(viewport.drawCountCallBuffer, 0);
                encoder.barrier(ComputeEncoder.BARRIER_SHADER | ComputeEncoder.BARRIER_INDIRECT,
                                ComputeEncoder.BARRIER_SHADER | ComputeEncoder.BARRIER_INDIRECT);
            }
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
        if (this.translucentTerrainShader != null) this.translucentTerrainShader.free();
        if (this.terrainShader != null) this.terrainShader.free();
        if (this.translucentTerrainPipeline != null) this.translucentTerrainPipeline.close();
        if (this.terrainPipeline != null) this.terrainPipeline.close();
        this.commandGenPipeline.close();
        this.cullPipeline.close();
        this.forceAllVisiblePipeline.close();
        this.prepPipeline.close();
        this.translucentGenPipeline.close();
        this.prefixSumPipeline.close();
        this.statisticsBuffer.free();
    }
}
