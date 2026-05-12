package me.cortex.voxy.client.core.rendering.hierachical;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import me.cortex.voxy.client.RenderStatistics;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.gl.shader.ShaderLoader;
import me.cortex.voxy.client.core.gpu.ComputeEncoder;
import me.cortex.voxy.client.core.gpu.ComputePipelineDesc;
import me.cortex.voxy.client.core.gpu.IGpuBuffer;
import me.cortex.voxy.client.core.gpu.IGpuPipeline;
import me.cortex.voxy.client.core.gpu.IGpuSampler;
import me.cortex.voxy.client.core.gpu.RenderBackend;
import me.cortex.voxy.client.core.gpu.RenderBackendFactory;
import me.cortex.voxy.client.core.gpu.SamplerDesc;
import me.cortex.voxy.client.core.rendering.Viewport;
import me.cortex.voxy.client.core.rendering.building.RenderGenerationService;
import me.cortex.voxy.client.core.rendering.util.DownloadStream;
import me.cortex.voxy.client.core.rendering.util.PrintfDebugUtil;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.world.WorldEngine;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_UNPACK_ROW_LENGTH;
import static org.lwjgl.opengl.GL11.GL_UNPACK_SKIP_PIXELS;
import static org.lwjgl.opengl.GL11.GL_UNPACK_SKIP_ROWS;
import static org.lwjgl.opengl.GL11.glPixelStorei;
import static org.lwjgl.opengl.GL12.GL_UNPACK_IMAGE_HEIGHT;
import static org.lwjgl.opengl.GL12.GL_UNPACK_SKIP_IMAGES;

/**
 * Hierarchical occlusion traverser. Walks the LOD octree on the GPU via a
 * compute shader, iterating per LOD level — each iteration's dispatch shape
 * is read indirectly from a metadata buffer the shader itself writes.
 *
 * M9 status: fully migrated onto the {@link RenderBackend} encoder
 * abstraction. Bind once per pass, dispatch indirect from
 * {@link #queueMetaBuffer}, flip-flop source/sink between iterations. The
 * single-int CPU→GPU writes ({@link #addTLN}/{@link #remTLN}) flow through
 * {@link UploadStream}, and counter resets ({@link #doTraversal},
 * {@link #downloadResetRequestQueue}) use {@code IGpuBuffer.zeroRange} —
 * so the class is fully backend-agnostic (M12 chunk 6 prep).
 */
public class HierarchicalOcclusionTraverser {
    public static final boolean HIERARCHICAL_SHADER_DEBUG = System.getProperty("voxy.hierarchicalShaderDebug", "false").equals("true");

    public static final int MAX_REQUEST_QUEUE_SIZE = 50;
    public static final int MAX_QUEUE_SIZE = 200_000;


    private static final int MAX_ITERATIONS = WorldEngine.MAX_LOD_LAYER + 1;
    private static final int LOCAL_WORK_SIZE_BITS = 5;
    private static final int LOCAL_WORK_SIZE = 1 << LOCAL_WORK_SIZE_BITS;

    /** UBO binding used to push queueIdx (see queue.glsl). */
    private static final int PUSH_BINDING = 14;

    private final AsyncNodeManager nodeManager;
    private final NodeCleaner nodeCleaner;
    private final RenderGenerationService meshGen;

    private final IGpuBuffer requestBuffer;

    private final IGpuBuffer nodeBuffer;
    private final IGpuBuffer uniformBuffer = RenderBackendFactory.get().createBuffer(1024).zero();
    private final IGpuBuffer statisticsBuffer = RenderBackendFactory.get().createBuffer(1024).zero();


    private int topNodeCount;
    private final Int2IntOpenHashMap topNode2idxMapping = new Int2IntOpenHashMap();//Used to store mapping from TLN to array index
    private final int[] idx2topNodeMapping = new int[MAX_QUEUE_SIZE];//Used to map idx to TLN id
    private final IGpuBuffer topNodeIds = RenderBackendFactory.get().createBuffer(MAX_QUEUE_SIZE * 4).zero();
    private final IGpuBuffer queueMetaBuffer = RenderBackendFactory.get().createBuffer(4 * 4 * MAX_ITERATIONS).zero();
    private final IGpuBuffer scratchQueueA = RenderBackendFactory.get().createBuffer(MAX_QUEUE_SIZE * 4).zero();
    private final IGpuBuffer scratchQueueB = RenderBackendFactory.get().createBuffer(MAX_QUEUE_SIZE * 4).zero();

    private static int BINDING_COUNTER = 1;
    private static final int SCENE_UNIFORM_BINDING = BINDING_COUNTER++;
    private static final int REQUEST_QUEUE_BINDING = BINDING_COUNTER++;
    private static final int RENDER_QUEUE_BINDING = BINDING_COUNTER++;
    private static final int NODE_DATA_BINDING = BINDING_COUNTER++;
    /** Reserved — old GL build used this as a uniform location for queueIdx. queue.glsl now pushes via PUSH_BINDING UBO. */
    private static final int NODE_QUEUE_INDEX_BINDING_RESERVED = BINDING_COUNTER++;
    private static final int NODE_QUEUE_META_BINDING = BINDING_COUNTER++;
    private static final int NODE_QUEUE_SOURCE_BINDING = BINDING_COUNTER++;
    private static final int NODE_QUEUE_SINK_BINDING = BINDING_COUNTER++;
    private static final int RENDER_TRACKER_BINDING = BINDING_COUNTER++;
    private static final int STATISTICS_BUFFER_BINDING = BINDING_COUNTER++;

    /** HiZ sampled-texture slot (texture-unit equivalent). */
    private static final int HIZ_BINDING = 0;

    private final RenderBackend backend = RenderBackendFactory.get();
    private final IGpuSampler hizSampler = this.backend.createSampler(SamplerDesc.builder()
            .filter(SamplerDesc.Filter.NEAREST, SamplerDesc.Filter.NEAREST)
            .mipFilter(SamplerDesc.MipFilter.NEAREST)
            .wrap(SamplerDesc.Wrap.CLAMP_TO_EDGE, SamplerDesc.Wrap.CLAMP_TO_EDGE)
            .label("hizSampler")
            .build());

    private final IGpuPipeline traversal;


    public HierarchicalOcclusionTraverser(AsyncNodeManager nodeManager, NodeCleaner nodeCleaner, RenderGenerationService meshGen) {
        this.nodeCleaner = nodeCleaner;
        this.nodeManager = nodeManager;
        this.meshGen = meshGen;
        this.requestBuffer = RenderBackendFactory.get().createBuffer(MAX_REQUEST_QUEUE_SIZE * 8L + 8).zero();
        this.nodeBuffer = RenderBackendFactory.get().createBuffer(nodeManager.maxNodeCount * 16L).fill(-1);

        this.traversal = this.backend.createComputePipeline(new ComputePipelineDesc(
                ShaderLoader.parse("voxy:lod/hierarchical/traversal_dev.comp"),
                traversalDefines(),
                null, null,
                LOCAL_WORK_SIZE, 1, 1,
                "HierarchicalOcclusionTraverser.traversal"));

        this.topNode2idxMapping.defaultReturnValue(-1);
        this.nodeManager.setTLNAddRemoveCallbacks(this::addTLN, this::remTLN);
    }

    private static Map<String, String> traversalDefines() {
        var m = new LinkedHashMap<String, String>();
        if (HIERARCHICAL_SHADER_DEBUG) m.put("DEBUG", "");
        m.put("MAX_ITERATIONS", Integer.toString(MAX_ITERATIONS));
        m.put("LOCAL_SIZE_BITS", Integer.toString(LOCAL_WORK_SIZE_BITS));
        m.put("MAX_REQUEST_QUEUE_SIZE", Integer.toString(MAX_REQUEST_QUEUE_SIZE));
        m.put("HIZ_BINDING", Integer.toString(HIZ_BINDING));
        m.put("SCENE_UNIFORM_BINDING", Integer.toString(SCENE_UNIFORM_BINDING));
        m.put("REQUEST_QUEUE_BINDING", Integer.toString(REQUEST_QUEUE_BINDING));
        m.put("RENDER_QUEUE_BINDING", Integer.toString(RENDER_QUEUE_BINDING));
        m.put("NODE_DATA_BINDING", Integer.toString(NODE_DATA_BINDING));
        m.put("NODE_QUEUE_META_BINDING", Integer.toString(NODE_QUEUE_META_BINDING));
        m.put("NODE_QUEUE_SOURCE_BINDING", Integer.toString(NODE_QUEUE_SOURCE_BINDING));
        m.put("NODE_QUEUE_SINK_BINDING", Integer.toString(NODE_QUEUE_SINK_BINDING));
        m.put("RENDER_TRACKER_BINDING", Integer.toString(RENDER_TRACKER_BINDING));
        m.put("PUSH_BINDING", Integer.toString(PUSH_BINDING));
        if (RenderStatistics.enabled) {
            m.put("HAS_STATISTICS", "");
            m.put("STATISTICS_BUFFER_BINDING", Integer.toString(STATISTICS_BUFFER_BINDING));
        }
        return m;
    }

    private void addTLN(int id) {
        int aid = this.topNodeCount++;//Increment buffer
        if (this.topNodeCount > this.topNodeIds.size() / 4) {
            throw new IllegalStateException("Top level node count greater than capacity");
        }

        // M12 chunk 6 prep: route through UploadStream (cross-backend) instead
        // of raw glBindBuffer + nglBufferSubData. On Metal the previous raw GL
        // pattern would have written into MC's GL context against a meaningless
        // ID (MetalBuffer.id() is a Metal-internal handle, not a GL buffer name).
        long ptr = UploadStream.INSTANCE.upload(this.topNodeIds, aid * 4L, 4);
        MemoryUtil.memPutInt(ptr, id);
        UploadStream.INSTANCE.commit();

        if (this.topNode2idxMapping.put(id, aid) != -1) {
            throw new IllegalStateException();
        }
        this.idx2topNodeMapping[aid] = id;
    }

    private void remTLN(int id) {
        int idx = this.topNode2idxMapping.remove(id);
        this.topNodeCount--;
        if (idx == -1) {
            throw new IllegalStateException();
        }
        if (idx == this.topNodeCount) return;

        int endTLNId = this.idx2topNodeMapping[this.topNodeCount];
        this.idx2topNodeMapping[idx] = endTLNId;
        if (this.topNode2idxMapping.put(endTLNId, idx) == -1)
            throw new IllegalStateException();

        long ptr = UploadStream.INSTANCE.upload(this.topNodeIds, idx * 4L, 4);
        MemoryUtil.memPutInt(ptr, endTLNId);
        UploadStream.INSTANCE.commit();
    }

    private static void setFrustum(Viewport<?> viewport, long ptr) {
        for (int i = 0; i < 6; i++) {
            var plane = viewport.frustumPlanes[i];
            plane.getToAddress(ptr); ptr += 4 * 4;
        }
    }

    private void uploadUniform(Viewport<?> viewport) {
        long ptr = UploadStream.INSTANCE.upload(this.uniformBuffer, 0, 1024);

        viewport.MVP.getToAddress(ptr); ptr += 4 * 4 * 4;
        viewport.section.getToAddress(ptr); ptr += 4 * 3;
        MemoryUtil.memPutInt(ptr, viewport.hiZBuffer.getPackedLevels()); ptr += 4;
        viewport.innerTranslation.getToAddress(ptr); ptr += 4 * 3;

        final float screenspaceAreaDecreasingSize = VoxyConfig.CONFIG.subDivisionSize * VoxyConfig.CONFIG.subDivisionSize;
        MemoryUtil.memPutFloat(ptr, (float) (screenspaceAreaDecreasingSize) / (viewport.width * viewport.height)); ptr += 4;
        setFrustum(viewport, ptr); ptr += 4 * 4 * 6;
        MemoryUtil.memPutInt(ptr, (int) (viewport.getRenderList().size() / 4 - 1)); ptr += 4;
        MemoryUtil.memPutInt(ptr, this.nodeCleaner.visibilityId); ptr += 4;

        {
            final double TARGET_COUNT = 4000;
            double iFillness = Math.max(0, (TARGET_COUNT - this.meshGen.getTaskCount()) / TARGET_COUNT);
            iFillness = Math.pow(iFillness, 2);
            final int requestSize = (int) Math.ceil(iFillness * MAX_REQUEST_QUEUE_SIZE);
            MemoryUtil.memPutInt(ptr, Math.max(0, Math.min(MAX_REQUEST_QUEUE_SIZE, requestSize))); ptr += 4;
        }
    }

    public void doTraversal(Viewport<?> viewport) {
        this.uploadUniform(viewport);

        // PrintfDebugUtil binds a debug SSBO on its own pre-existing path;
        // gated on -Dvoxy.enableShaderDebugPrintf=true (default off). Stays
        // outside the encoder for now.
        PrintfDebugUtil.bind();

        if (RenderStatistics.enabled) {
            this.statisticsBuffer.zero();
        }

        //Clear the render output counter
        viewport.getRenderList().zeroRange(0, 4);

        this.traverseInternal(viewport);
        this.downloadResetRequestQueue();

        if (RenderStatistics.enabled) {
            DownloadStream.INSTANCE.download(this.statisticsBuffer, down -> {
                for (int i = 0; i < MAX_ITERATIONS; i++) {
                    RenderStatistics.hierarchicalTraversalCounts[i] = MemoryUtil.memGetInt(down.address + i * 4L);
                }
                for (int i = 0; i < MAX_ITERATIONS; i++) {
                    RenderStatistics.hierarchicalRenderSections[i] = MemoryUtil.memGetInt(down.address + MAX_ITERATIONS * 4L + i * 4L);
                }
            });
        }
    }

    private void traverseInternal(Viewport<?> viewport) {
        {
            //Fix mesa bug — these stick around between texture uploads and need resetting.
            glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            glPixelStorei(GL_UNPACK_IMAGE_HEIGHT, 0);
            glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
            glPixelStorei(GL_UNPACK_SKIP_IMAGES, 0);
        }

        int firstDispatchSize = (this.topNodeCount + LOCAL_WORK_SIZE - 1) >> LOCAL_WORK_SIZE_BITS;

        {
            //TODO:FIXME: THIS IS BULLSHIT BY INTEL need to fix the clearing
            long ptr = UploadStream.INSTANCE.upload(this.queueMetaBuffer, 0, 16 * MAX_ITERATIONS);
            MemoryUtil.memPutInt(ptr +  0, firstDispatchSize);
            MemoryUtil.memPutInt(ptr +  4, 1);
            MemoryUtil.memPutInt(ptr +  8, 1);
            MemoryUtil.memPutInt(ptr + 12, this.topNodeCount);
            for (int i = 1; i < MAX_ITERATIONS; i++) {
                MemoryUtil.memPutInt(ptr + (i * 16) +  0, 0);
                MemoryUtil.memPutInt(ptr + (i * 16) +  4, 1);
                MemoryUtil.memPutInt(ptr + (i * 16) +  8, 1);
                MemoryUtil.memPutInt(ptr + (i * 16) + 12, 0);
            }
            UploadStream.INSTANCE.commit();
        }

        try (ComputeEncoder encoder = this.backend.beginComputePass();
             MemoryStack stack = MemoryStack.stackPush()) {
            long pushAddr = stack.nmalloc(4);

            encoder.setPipeline(this.traversal);

            // Bindings that don't change between iterations — bound once.
            encoder.setBuffer(SCENE_UNIFORM_BINDING, this.uniformBuffer, 0);
            encoder.setBuffer(REQUEST_QUEUE_BINDING, this.requestBuffer, 0);
            encoder.setBuffer(RENDER_QUEUE_BINDING, viewport.getRenderList(), 0);
            encoder.setBuffer(NODE_DATA_BINDING, this.nodeBuffer, 0);
            encoder.setBuffer(NODE_QUEUE_META_BINDING, this.queueMetaBuffer, 0);
            encoder.setBuffer(RENDER_TRACKER_BINDING, this.nodeCleaner.visibilityBuffer, 0);
            if (RenderStatistics.enabled) {
                encoder.setBuffer(STATISTICS_BUFFER_BINDING, this.statisticsBuffer, 0);
            }
            encoder.setTexture(HIZ_BINDING, viewport.hiZBuffer.getHizTexture());
            encoder.setSampler(HIZ_BINDING, this.hizSampler);

            // --- Iteration 0: direct dispatch with explicit group count.
            MemoryUtil.memPutInt(pushAddr, 0);
            encoder.setBytes(PUSH_BINDING, pushAddr, 4);
            encoder.setBuffer(NODE_QUEUE_SOURCE_BINDING, this.topNodeIds, 0);
            encoder.setBuffer(NODE_QUEUE_SINK_BINDING, this.scratchQueueB, 0);

            encoder.barrier(
                    ComputeEncoder.BARRIER_SHADER | ComputeEncoder.BARRIER_INDIRECT | ComputeEncoder.BARRIER_TRANSFER,
                    ComputeEncoder.BARRIER_SHADER | ComputeEncoder.BARRIER_INDIRECT);
            encoder.dispatch(firstDispatchSize, 1, 1);
            encoder.barrier(
                    ComputeEncoder.BARRIER_SHADER | ComputeEncoder.BARRIER_INDIRECT,
                    ComputeEncoder.BARRIER_SHADER | ComputeEncoder.BARRIER_INDIRECT);

            // --- Iterations 1..MAX-1: indirect dispatch, flip-flop source/sink.
            for (int iter = 1; iter < MAX_ITERATIONS; iter++) {
                MemoryUtil.memPutInt(pushAddr, iter);
                encoder.setBytes(PUSH_BINDING, pushAddr, 4);

                IGpuBuffer source = ((iter & 1) == 0 ? this.scratchQueueA : this.scratchQueueB);
                IGpuBuffer sink = ((iter & 1) == 0 ? this.scratchQueueB : this.scratchQueueA);
                encoder.setBuffer(NODE_QUEUE_SOURCE_BINDING, source, 0);
                encoder.setBuffer(NODE_QUEUE_SINK_BINDING, sink, 0);

                encoder.barrier(
                        ComputeEncoder.BARRIER_SHADER | ComputeEncoder.BARRIER_INDIRECT,
                        ComputeEncoder.BARRIER_SHADER | ComputeEncoder.BARRIER_INDIRECT);
                encoder.dispatchIndirect(this.queueMetaBuffer, iter * 4L * 4);
            }

            encoder.barrier(
                    ComputeEncoder.BARRIER_SHADER | ComputeEncoder.BARRIER_INDIRECT,
                    ComputeEncoder.BARRIER_SHADER | ComputeEncoder.BARRIER_TRANSFER);
        }
    }


    private void downloadResetRequestQueue() {
        DownloadStream.INSTANCE.download(this.requestBuffer, this::forwardDownloadResult);
        // M12 chunk 6 prep: cross-backend zero (was raw glBindBuffer +
        // nglBufferSubData(null) which is UB-on-strict-drivers and outright
        // broken on Metal where the buffer id isn't a GL name).
        this.requestBuffer.zeroRange(0, 4);
    }

    private void forwardDownloadResult(long ptr, long size) {
        int count = MemoryUtil.memGetInt(ptr); ptr += 8;
        if (count < 0 || count > 50000) {
            Logger.error(new IllegalStateException("Count unexpected extreme value: " + count + " things may get weird"));
            return;
        }
        if (count > (this.requestBuffer.size() >> 3) - 1) {
            count = (int) ((this.requestBuffer.size() >> 3) - 1);
            MemoryUtil.memPutInt(ptr - 8, count);
        }
        if (count != 0) {
            this.nodeManager.submitRequestBatch(new MemoryBuffer(count * 8L + 8).cpyFrom(ptr - 8));
        }
    }

    public IGpuBuffer getNodeBuffer() {
        return this.nodeBuffer;
    }

    public void free() {
        this.traversal.close();
        this.requestBuffer.free();
        this.nodeBuffer.free();
        this.uniformBuffer.free();
        this.statisticsBuffer.free();
        this.queueMetaBuffer.free();
        this.topNodeIds.free();
        this.scratchQueueA.free();
        this.scratchQueueB.free();
        this.hizSampler.close();
    }
}
