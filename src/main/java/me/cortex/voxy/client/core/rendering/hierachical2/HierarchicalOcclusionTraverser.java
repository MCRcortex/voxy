package me.cortex.voxy.client.core.rendering.hierachical2;

import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.rendering.util.HiZBuffer;
import me.cortex.voxy.client.core.rendering.Viewport;
import me.cortex.voxy.client.core.rendering.util.DownloadStream;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL30.GL_R32UI;
import static org.lwjgl.opengl.GL30C.GL_RED_INTEGER;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL43.glDispatchComputeIndirect;
import static org.lwjgl.opengl.GL45.nglClearNamedBufferSubData;

public class HierarchicalOcclusionTraverser {
    private final HierarchicalNodeManager nodeManager;

    private final int maxRequestCount;
    private final GlBuffer requestBuffer;

    private final GlBuffer nodeBuffer;
    private final GlBuffer uniformBuffer = new GlBuffer(1024).zero();
    private final GlBuffer renderList = new GlBuffer(100_000 * 4 + 4).zero();//100k sections max to render

    private final HiZBuffer hiZBuffer = new HiZBuffer();



    public HierarchicalOcclusionTraverser(HierarchicalNodeManager nodeManager, int requestBufferCount) {
        this.nodeManager = nodeManager;
        this.requestBuffer = new GlBuffer(requestBufferCount*4L+1024).zero();//The 1024 is to assist with race condition issues
        this.nodeBuffer = new GlBuffer(nodeManager.maxNodeCount*16L).zero();
        this.maxRequestCount = requestBufferCount;
    }

    private void uploadUniform(Viewport<?> viewport) {

    }

    public static int HACKY_SECTION_COUNT = 0;
    public void doTraversal(Viewport<?> viewport, int depthBuffer) {
        //Compute the mip chain
        this.hiZBuffer.buildMipChain(depthBuffer, viewport.width, viewport.height);

        this.uploadUniform(viewport);
        UploadStream.INSTANCE.commit();

        //Use a chain of glDispatchComputeIndirect (5 times) with alternating read/write buffers
        // TODO: swap to persistent gpu thread instead

        if (HACKY_SECTION_COUNT != 0) {
            long uploadPtr = UploadStream.INSTANCE.upload(this.renderList, 0, HACKY_SECTION_COUNT*4L+4);

            MemoryUtil.memPutInt(uploadPtr, HACKY_SECTION_COUNT);
            for (int i = 1; i < HACKY_SECTION_COUNT+1; i++) {
                MemoryUtil.memPutInt(uploadPtr + 4L * i, i - 1);
            }

            UploadStream.INSTANCE.commit();
        }

        this.downloadResetRequestQueue();
    }

    private void downloadResetRequestQueue() {
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        DownloadStream.INSTANCE.download(this.requestBuffer, this::forwardDownloadResult);
        DownloadStream.INSTANCE.commit();
        nglClearNamedBufferSubData(this.requestBuffer.id, GL_R32UI, 0, 4, GL_RED_INTEGER, GL_UNSIGNED_INT, 0);
    }

    public GlBuffer getRenderListBuffer() {
        return this.renderList;
    }

    private void forwardDownloadResult(long ptr, long size) {
        int count = MemoryUtil.memGetInt(ptr);
        if (count < 0 || count > 50000) {
            throw new IllegalStateException("Count unexpected extreme value: " + count);
        }
        if (count > (this.requestBuffer.size()>>2)-1) {
            throw new IllegalStateException("Count over max buffer size, desync expected, aborting");
        }
        if (count > this.maxRequestCount) {
            System.err.println("Count larger than 'maxRequestCount', overflow captured. Overflowed by " + (count-this.maxRequestCount));
        }
        if (count != 0) {
            this.nodeManager.processRequestQueue(count, ptr + 4);
        }
    }

    public void free() {
        this.requestBuffer.free();
        this.hiZBuffer.free();
        this.nodeBuffer.free();
        this.uniformBuffer.free();
        this.renderList.free();
    }
}
