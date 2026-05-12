package me.cortex.voxy.client.core.rendering.hierachical;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import me.cortex.voxy.client.core.gl.shader.ShaderLoader;
import me.cortex.voxy.client.core.gpu.ComputeEncoder;
import me.cortex.voxy.client.core.gpu.ComputePipelineDesc;
import me.cortex.voxy.client.core.gpu.IGpuBuffer;
import me.cortex.voxy.client.core.gpu.IGpuPipeline;
import me.cortex.voxy.client.core.gpu.RenderBackend;
import me.cortex.voxy.client.core.gpu.RenderBackendFactory;
import me.cortex.voxy.client.core.rendering.util.DownloadStream;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import org.lwjgl.opengl.ARBDirectStateAccess;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.util.LinkedHashMap;
import java.util.Map;


/**
 * Hierarchical-traversal sub-pass that keeps Voxy's GPU node tables tidy:
 *
 *  1. Sort the visibility buffer to surface the {@value #OUTPUT_COUNT} least-recently-touched nodes.
 *  2. Transform those raw ids into (node-id, geometry-pointer) pairs and stream the
 *     result back to CPU so {@link AsyncNodeManager#submitRemoveBatch} can recycle them.
 *  3. Provide a separate "batch clear" path that rewrites visibility entries for a
 *     CPU-supplied id list (used when geometry is reallocated).
 *
 * M9 status: tick() runs through the {@link RenderBackend} encoder abstraction and so
 * works on any backend that implements compute. updateIds() still binds
 * {@link UploadStream}'s raw GL buffer id directly — UploadStream is not yet
 * migrated, so the binding is OpenGL-only for now.
 */
public class NodeCleaner {

    private static final int SORTING_WORKER_SIZE = 64;
    private static final int WORK_PER_THREAD = 8;
    static final int OUTPUT_COUNT = 256;

    /** UBO binding used to push small scalar uniforms (see PUSH_BINDING in the shaders). */
    private static final int PUSH_BINDING = 14;

    // SSBO binding indices — match the layout(binding = X) declarations in the
    // three .comp shaders so the encoder.setBuffer calls land on the right slot.
    private static final int SORT_VISIBILITY_BINDING = 1;
    private static final int SORT_OUTPUT_BINDING = 2;
    private static final int SORT_NODE_DATA_BINDING = 3;

    private static final int TRANSFORM_MIN_ID_BINDING = 0;
    private static final int TRANSFORM_NODE_BUFFER_BINDING = 1;
    private static final int TRANSFORM_OUTPUT_BINDING = 2;
    private static final int TRANSFORM_VISIBILITY_BINDING = 3;

    private static final int CLEAR_VISIBILITY_BINDING = 0;
    private static final int CLEAR_LIST_BINDING = 1;

    private final RenderBackend backend = RenderBackendFactory.get();
    private final IGpuPipeline sorter;
    private final IGpuPipeline resultTransformer;
    private final IGpuPipeline batchClear;

    final IGpuBuffer visibilityBuffer;
    private final IGpuBuffer outputBuffer = RenderBackendFactory.get()
            .createBuffer(OUTPUT_COUNT * 4 + OUTPUT_COUNT * 8); //Scratch + output

    private final AsyncNodeManager nodeManager;
    int visibilityId = 0;


    public NodeCleaner(AsyncNodeManager nodeManager) {
        this.nodeManager = nodeManager;
        this.visibilityBuffer = this.backend.createBuffer(nodeManager.maxNodeCount * 4L).zero();
        this.visibilityBuffer.fill(-1);

        this.sorter = this.backend.createComputePipeline(new ComputePipelineDesc(
                ShaderLoader.parse("voxy:lod/hierarchical/cleaner/sort_visibility.comp"),
                sorterDefines(),
                null, null,
                SORTING_WORKER_SIZE, 1, 1,
                "NodeCleaner.sort_visibility"));
        this.resultTransformer = this.backend.createComputePipeline(new ComputePipelineDesc(
                ShaderLoader.parse("voxy:lod/hierarchical/cleaner/result_transformer.comp"),
                resultTransformerDefines(),
                null, null,
                OUTPUT_COUNT, 1, 1,
                "NodeCleaner.result_transformer"));
        this.batchClear = this.backend.createComputePipeline(new ComputePipelineDesc(
                ShaderLoader.parse("voxy:lod/hierarchical/cleaner/batch_visibility_set.comp"),
                batchClearDefines(),
                null, null,
                128, 1, 1,
                "NodeCleaner.batch_visibility_set"));
    }

    private static Map<String, String> sorterDefines() {
        var m = new LinkedHashMap<String, String>();
        m.put("WORK_SIZE", Integer.toString(SORTING_WORKER_SIZE));
        m.put("ELEMS_PER_THREAD", Integer.toString(WORK_PER_THREAD));
        m.put("OUTPUT_SIZE", Integer.toString(OUTPUT_COUNT));
        m.put("VISIBILITY_BUFFER_BINDING", Integer.toString(SORT_VISIBILITY_BINDING));
        m.put("OUTPUT_BUFFER_BINDING", Integer.toString(SORT_OUTPUT_BINDING));
        m.put("NODE_DATA_BINDING", Integer.toString(SORT_NODE_DATA_BINDING));
        m.put("PUSH_BINDING", Integer.toString(PUSH_BINDING));
        return m;
    }

    private static Map<String, String> resultTransformerDefines() {
        var m = new LinkedHashMap<String, String>();
        m.put("OUTPUT_SIZE", Integer.toString(OUTPUT_COUNT));
        m.put("MIN_ID_BUFFER_BINDING", Integer.toString(TRANSFORM_MIN_ID_BINDING));
        m.put("NODE_BUFFER_BINDING", Integer.toString(TRANSFORM_NODE_BUFFER_BINDING));
        m.put("OUTPUT_BUFFER_BINDING", Integer.toString(TRANSFORM_OUTPUT_BINDING));
        m.put("VISIBILITY_BUFFER_BINDING", Integer.toString(TRANSFORM_VISIBILITY_BINDING));
        m.put("PUSH_BINDING", Integer.toString(PUSH_BINDING));
        return m;
    }

    private static Map<String, String> batchClearDefines() {
        var m = new LinkedHashMap<String, String>();
        m.put("VISIBILITY_BUFFER_BINDING", Integer.toString(CLEAR_VISIBILITY_BINDING));
        m.put("LIST_BUFFER_BINDING", Integer.toString(CLEAR_LIST_BINDING));
        m.put("PUSH_BINDING", Integer.toString(PUSH_BINDING));
        return m;
    }


    public void tick(IGpuBuffer nodeDataBuffer) {
        this.visibilityId++;
        if (!this.shouldCleanGeometry()) return;

        this.outputBuffer.fill(this.nodeManager.maxNodeCount - 2);

        try (ComputeEncoder encoder = this.backend.beginComputePass()) {
            // --- Pass 1: warp-level sort of the visibility buffer into outputBuffer[0..OUTPUT_COUNT). ---
            encoder.setPipeline(this.sorter);
            encoder.setBuffer(SORT_VISIBILITY_BINDING, this.visibilityBuffer, 0);
            encoder.setBuffer(SORT_OUTPUT_BINDING, this.outputBuffer, 0);
            encoder.setBuffer(SORT_NODE_DATA_BINDING, nodeDataBuffer, 0);

            encoder.barrier(ComputeEncoder.BARRIER_SHADER, ComputeEncoder.BARRIER_SHADER);

            int groups = (this.nodeManager.getCurrentMaxNodeId()
                    + (SORTING_WORKER_SIZE * WORK_PER_THREAD) - 1)
                    / (SORTING_WORKER_SIZE * WORK_PER_THREAD);
            encoder.dispatch(groups, 1, 1);

            // --- Pass 2: transform sorted ids → (node, geom-ptr) pairs in outputBuffer[OUTPUT_COUNT..]. ---
            encoder.setPipeline(this.resultTransformer);
            encoder.setBuffer(TRANSFORM_MIN_ID_BINDING, this.outputBuffer, 0);
            encoder.setBuffer(TRANSFORM_NODE_BUFFER_BINDING, nodeDataBuffer, 0);
            encoder.setBuffer(TRANSFORM_OUTPUT_BINDING, this.outputBuffer, 4L * OUTPUT_COUNT);
            encoder.setBuffer(TRANSFORM_VISIBILITY_BINDING, this.visibilityBuffer, 0);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                long addr = stack.nmalloc(4);
                MemoryUtil.memPutInt(addr, this.visibilityId);
                encoder.setBytes(PUSH_BINDING, addr, 4);
            }

            encoder.barrier(ComputeEncoder.BARRIER_SHADER, ComputeEncoder.BARRIER_SHADER);
            encoder.dispatch(1, 1, 1);
            encoder.barrier(ComputeEncoder.BARRIER_SHADER, ComputeEncoder.BARRIER_TRANSFER);
        }

        DownloadStream.INSTANCE.download(this.outputBuffer, 4 * OUTPUT_COUNT, 8 * OUTPUT_COUNT,
                buffer -> this.nodeManager.submitRemoveBatch(buffer.copy()));
    }

    private boolean shouldCleanGeometry() {
        long remaining = this.nodeManager.getGeometryCapacity()
                - this.nodeManager.getUsedGeometryCapacity();
        return remaining < 256_000_000; //If less than 256 mb free memory
    }

    public void updateIds(IntOpenHashSet collection) {
        if (collection.isEmpty()) return;

        int count = collection.size();
        long addr = UploadStream.INSTANCE.rawUploadAddress(count * 4 + 16);
        addr = (addr + 15) & ~15L; //Align to 16 bytes

        long ptr = UploadStream.INSTANCE.getBaseAddress() + addr;
        var iter = collection.iterator();
        while (iter.hasNext()) {
            MemoryUtil.memPutInt(ptr, iter.nextInt()); ptr += 4;
        }
        UploadStream.INSTANCE.commit();

        try (ComputeEncoder encoder = this.backend.beginComputePass()) {
            encoder.setPipeline(this.batchClear);
            encoder.setBuffer(CLEAR_VISIBILITY_BINDING, this.visibilityBuffer, 0);

            // M12: UploadStream's persistent buffer flows through the encoder's
            // IGpuPersistentBuffer overload now (was a raw glBindBufferRange
            // that broke on Metal because the buffer id isn't a GL name).
            encoder.setBuffer(CLEAR_LIST_BINDING, UploadStream.INSTANCE.getUploadBuffer(),
                    addr, count * 4L);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                long pushAddr = stack.nmalloc(8);
                MemoryUtil.memPutInt(pushAddr, count);
                MemoryUtil.memPutInt(pushAddr + 4, this.visibilityId);
                encoder.setBytes(PUSH_BINDING, pushAddr, 8);
            }

            encoder.barrier(ComputeEncoder.BARRIER_SHADER, ComputeEncoder.BARRIER_SHADER);
            encoder.dispatch((count + 127) / 128, 1, 1);
            encoder.barrier(ComputeEncoder.BARRIER_SHADER, ComputeEncoder.BARRIER_SHADER);
        }
    }

    private void dumpDebugData() {
        int[] outData = new int[OUTPUT_COUNT * 3];
        ARBDirectStateAccess.glGetNamedBufferSubData(this.outputBuffer.id(), 0, outData);
        for (int i = 0; i < OUTPUT_COUNT; i++) {
            System.out.println(outData[i]);
        }
        int[] visData = new int[(int) (this.visibilityBuffer.size() / 4)];
        ARBDirectStateAccess.glGetNamedBufferSubData(this.visibilityBuffer.id(), 0, visData);
    }

    public void free() {
        this.sorter.close();
        this.visibilityBuffer.free();
        this.outputBuffer.free();
        this.batchClear.close();
        this.resultTransformer.close();
    }
}
