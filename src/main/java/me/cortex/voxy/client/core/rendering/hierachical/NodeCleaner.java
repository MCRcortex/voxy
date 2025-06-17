package me.cortex.voxy.client.core.rendering.hierachical;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.shader.AutoBindingShader;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.rendering.util.PrintfDebugUtil;
import me.cortex.voxy.client.core.rendering.util.DownloadStream;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import org.lwjgl.opengl.ARBDirectStateAccess;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.ARBDirectStateAccess.glCopyNamedBufferSubData;
import static org.lwjgl.opengl.GL30C.glBindBufferRange;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static org.lwjgl.opengl.GL43C.*;

//Uses compute shaders to compute the last 256 rendered section (64x64 workgroup size maybe)
// done via warp level sort, then workgroup sort (shared memory), (/w sorting network)
// then use bubble sort (/w fast path going to middle or 2 subdivisions deep) the bubble it up
// can do incremental sorting pass aswell, so only scan and sort a rolling sector of sections
// (over a few frames to not cause lag, maybe)


//TODO : USE THIS IN HierarchicalOcclusionTraverser instead of other shit
public class NodeCleaner {
    //TODO: use batch_visibility_set to clear visibility data when nodes are removed!! (TODO: nodeManager will need to forward info to this)


    private static final int SORTING_WORKER_SIZE = 64;
    private static final int WORK_PER_THREAD = 8;
    static final int OUTPUT_COUNT = 256;


    private final AutoBindingShader sorter = Shader.makeAuto(PrintfDebugUtil.PRINTF_processor)
            .define("WORK_SIZE", SORTING_WORKER_SIZE)
            .define("ELEMS_PER_THREAD", WORK_PER_THREAD)
            .define("OUTPUT_SIZE", OUTPUT_COUNT)
            .define("VISIBILITY_BUFFER_BINDING", 1)
            .define("OUTPUT_BUFFER_BINDING", 2)
            .define("NODE_DATA_BINDING", 3)
            .add(ShaderType.COMPUTE, "voxy:lod/hierarchical/cleaner/sort_visibility.comp")
            .compile();

    private final AutoBindingShader resultTransformer = Shader.makeAuto()
            .define("OUTPUT_SIZE", OUTPUT_COUNT)
            .define("MIN_ID_BUFFER_BINDING", 0)
            .define("NODE_BUFFER_BINDING", 1)
            .define("OUTPUT_BUFFER_BINDING", 2)
            .define("VISIBILITY_BUFFER_BINDING", 3)
            .add(ShaderType.COMPUTE, "voxy:lod/hierarchical/cleaner/result_transformer.comp")
            .compile();

    private final AutoBindingShader batchClear = Shader.makeAuto()
            .define("VISIBILITY_BUFFER_BINDING", 0)
            .define("LIST_BUFFER_BINDING", 1)
            .add(ShaderType.COMPUTE, "voxy:lod/hierarchical/cleaner/batch_visibility_set.comp")
            .compile();


    final GlBuffer visibilityBuffer;
    private final GlBuffer outputBuffer = new GlBuffer(OUTPUT_COUNT*4+OUTPUT_COUNT*8);//Scratch + output

    private final AsyncNodeManager nodeManager;
    int visibilityId = 0;


    public NodeCleaner(AsyncNodeManager nodeManager) {
        this.nodeManager = nodeManager;
        this.visibilityBuffer = new GlBuffer(nodeManager.maxNodeCount*4L).zero();
        this.visibilityBuffer.fill(-1);

        this.batchClear
                .ssbo("VISIBILITY_BUFFER_BINDING", this.visibilityBuffer);

        this.sorter
                .ssbo("VISIBILITY_BUFFER_BINDING", this.visibilityBuffer)
                .ssbo("OUTPUT_BUFFER_BINDING", this.outputBuffer);

        /*
        this.nodeManager.setClear(new NodeManager.ICleaner() {
            @Override
            public void alloc(int id) {
                NodeCleaner.this.allocIds.add(id);
                NodeCleaner.this.freeIds.remove(id);
            }

            @Override
            public void move(int from, int to) {
                NodeCleaner.this.allocIds.remove(to);
                glCopyNamedBufferSubData(NodeCleaner.this.visibilityBuffer.id, NodeCleaner.this.visibilityBuffer.id, 4L*from, 4L*to, 4);
            }

            @Override
            public void free(int id) {
                NodeCleaner.this.freeIds.add(id);
                NodeCleaner.this.allocIds.remove(id);
            }
        });
         */
    }


    public void tick(GlBuffer nodeDataBuffer) {
        this.visibilityId++;
        if (this.shouldCleanGeometry()) {
            this.outputBuffer.fill(this.nodeManager.maxNodeCount - 2);//TODO: maybe dont set to zero??

            this.sorter.bind();
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, nodeDataBuffer.id);

            //TODO: choose whether this is in nodeSpace or section/geometryId space
            //
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
            //This should (IN THEORY naturally align its self to the pow2 max boarder, if not... well undefined behavior is ok right?)
            glDispatchCompute((this.nodeManager.getCurrentMaxNodeId() + (SORTING_WORKER_SIZE*WORK_PER_THREAD) - 1) / (SORTING_WORKER_SIZE*WORK_PER_THREAD), 1, 1);

            this.resultTransformer.bind();
            glBindBufferRange(GL_SHADER_STORAGE_BUFFER, 0, this.outputBuffer.id, 0, 4 * OUTPUT_COUNT);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, nodeDataBuffer.id);
            glBindBufferRange(GL_SHADER_STORAGE_BUFFER, 2, this.outputBuffer.id, 4 * OUTPUT_COUNT, 8 * OUTPUT_COUNT);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, this.visibilityBuffer.id);
            glUniform1ui(0, this.visibilityId);

            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
            glDispatchCompute(1, 1, 1);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

            DownloadStream.INSTANCE.download(this.outputBuffer, 4 * OUTPUT_COUNT, 8 * OUTPUT_COUNT,
                    buffer -> this.nodeManager.submitRemoveBatch(buffer.copy())//Copy into buffer and emit to node manager
            );
        }
    }

    private boolean shouldCleanGeometry() {
        if (false) {
            //If used more than 75% of geometry buffer
            long used = this.nodeManager.getUsedGeometryCapacity();
            return 3 < ((double) used) / ((double) (this.nodeManager.getGeometryCapacity() - used));
        } else {
            long remaining = this.nodeManager.getGeometryCapacity() - this.nodeManager.getUsedGeometryCapacity();
            return remaining < 256_000_000;//If less than 256 mb free memory
        }
    }

    public void updateIds(IntOpenHashSet collection) {
        if (!collection.isEmpty()) {
            int count = collection.size();
            long addr = UploadStream.INSTANCE.rawUploadAddress(count * 4 + 16);//TODO ensure alignment, create method todo alignment things
            addr = (addr+15)&~15L;//Align to 16 bytes

            long ptr = UploadStream.INSTANCE.getBaseAddress() + addr;
            var iter = collection.iterator();
            while (iter.hasNext()) {
                MemoryUtil.memPutInt(ptr, iter.nextInt()); ptr+=4;
            }
            UploadStream.INSTANCE.commit();

            this.batchClear.bind();
            glBindBufferRange(GL_SHADER_STORAGE_BUFFER, 1, UploadStream.INSTANCE.getRawBufferId(), addr, count*4L);
            glUniform1ui(0, count);
            glUniform1ui(1, this.visibilityId);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
            glDispatchCompute((count+127)/128, 1, 1);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        }
    }

    private void dumpDebugData() {
        int[] outData = new int[OUTPUT_COUNT*3];
        ARBDirectStateAccess.glGetNamedBufferSubData(this.outputBuffer.id, 0, outData);
        for(int i =0;i < OUTPUT_COUNT; i++) {
            System.out.println(outData[i]);
        }
        /*
        System.out.println("---------------\n");
        for(int i =0;i < OUTPUT_COUNT; i++) {
            System.out.println(data[i*2+OUTPUT_COUNT]+", "+data[i*2+OUTPUT_COUNT+1]);
        }*/
        int[] visData = new int[(int) (this.visibilityBuffer.size()/4)];
        ARBDirectStateAccess.glGetNamedBufferSubData(this.visibilityBuffer.id, 0, visData);
        int a = 0;
    }

    public void free() {
        this.sorter.free();
        this.visibilityBuffer.free();
        this.outputBuffer.free();
        this.batchClear.free();
        this.resultTransformer.free();
    }
}
