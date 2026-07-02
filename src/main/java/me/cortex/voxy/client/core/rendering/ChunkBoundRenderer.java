package me.cortex.voxy.client.core.rendering;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.cortex.voxy.client.core.AbstractRenderPipeline;
import me.cortex.voxy.client.core.RenderProperties;
import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.GlVertexArray;
import me.cortex.voxy.client.core.gl.shader.AutoBindingShader;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderLoader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.rendering.util.SharedIndexBuffer;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.MemoryBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.ARBDirectStateAccess.glCopyNamedBufferSubData;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL31.glDrawElementsInstanced;
import static org.lwjgl.opengl.GL42.glDrawElementsInstancedBaseInstance;

//This is a render subsystem, its very simple in what it does
// it renders an AABB around loaded chunks, thats it
public class ChunkBoundRenderer {
    private static final int INIT_MAX_CHUNK_COUNT = 1<<12;
    private GlBuffer chunkPosBuffer = new GlBuffer(INIT_MAX_CHUNK_COUNT*8);//Stored as ivec2
    private final GlBuffer uniformBuffer = new GlBuffer(128);
    private final Long2IntOpenHashMap chunk2idx = new Long2IntOpenHashMap(INIT_MAX_CHUNK_COUNT);
    private long[] idx2chunk = new long[INIT_MAX_CHUNK_COUNT];
    private final Shader rasterShader;
    private final RenderProperties properties;
    private boolean previousFrameWasExact;

    private final LongOpenHashSet addQueue = new LongOpenHashSet();
    private final LongOpenHashSet remQueue = new LongOpenHashSet();

    private final AbstractRenderPipeline pipeline;
    public ChunkBoundRenderer(AbstractRenderPipeline pipeline) {
        this.chunk2idx.defaultReturnValue(-1);
        this.properties = pipeline.properties;

        String vert = ShaderLoader.parse("voxy:chunkoutline/outline.vsh");
        String taa = pipeline.taaFunction("getTAA");
        if (taa != null) {
            this.pipeline = pipeline;
            vert = vert+"\n\n\n"+taa;
        } else {
            this.pipeline = null;
        }

        this.rasterShader = Shader.makeAuto()
                .addSource(ShaderType.VERTEX, vert)
                .defineIf("TAA", taa != null)
                .add(ShaderType.FRAGMENT, "voxy:chunkoutline/outline.fsh")
                .apply(this.properties::apply)
                .compile()
                .ubo(0, this.uniformBuffer)
                .ssbo(1, this.chunkPosBuffer);
    }

    public void addSection(long pos) {
        if (!this.remQueue.remove(pos)) {
            this.addQueue.add(pos);
        }
    }

    public void removeSection(long pos) {
        if (!this.addQueue.remove(pos)) {
            this.remQueue.add(pos);
        }
    }

    //Bind and render, changing as little gl state as possible so that the caller may configure how it wants to render
    public void render(Viewport<?> viewport, boolean renderExactBounds) {
        if (this.previousFrameWasExact && !renderExactBounds) {
            //We need to reupload the buffer cause we trash it during exact render bound rendering
            long addr = UploadStream.INSTANCE.upload(this.chunkPosBuffer, 0, this.chunk2idx.size()*8);
            for (int i = 0; i < this.chunk2idx.size(); i++) {
                putPos(addr, this.idx2chunk[i]); addr+=8;
            }
            UploadStream.INSTANCE.commit();
        }
        if (!this.remQueue.isEmpty()) {
            boolean wasEmpty = this.chunk2idx.isEmpty();
            this.remQueue.forEach(this::_remPos);//TODO: REPLACE WITH SCATTER COMPUTE
            this.remQueue.clear();
            if (this.chunk2idx.isEmpty()&&!wasEmpty) {//When going from stuff to nothing need to clear the depth buffer
                viewport.depthBoundingBuffer.clear(this.properties.inverseClearDepth());
            }
        }
        final float renderDistance = Minecraft.getInstance().options.getEffectiveRenderDistance()*16;//In blocks

        int count = this.chunk2idx.size();
        if (renderExactBounds) {
            {
                long addr = UploadStream.INSTANCE.uploadTo(this.chunkPosBuffer);
                count = findEmitBoundingChunks(viewport, renderDistance, (int) (this.chunkPosBuffer.size() / 8), addr);
                UploadStream.INSTANCE.commit();
            }
            if (count<0) {
                this.chunkPosBuffer.free();//Destroy old
                var chunkPoss = new MemoryBuffer((-count) * 8L);
                count = findEmitBoundingChunks(viewport, renderDistance, -count, chunkPoss.address);
                if (count<0) {
                    chunkPoss.free();
                    throw new IllegalStateException("Not sure how this is possible, should have exact capacity. badness: " + count);
                }
                this.chunkPosBuffer = new GlBuffer(chunkPoss);//Setup new buffer with the new data
                chunkPoss.free();
                ((AutoBindingShader)this.rasterShader).ssbo(1, this.chunkPosBuffer);
            }
        }

        this.renderInner(viewport, renderDistance, count);


        if (!this.addQueue.isEmpty()) {
            this.addQueue.forEach(this::_addPos);//TODO: REPLACE WITH SCATTER COMPUTE
            this.addQueue.clear();
            UploadStream.INSTANCE.commit();
        }
        this.previousFrameWasExact = renderExactBounds;
    }

    private void renderInner(Viewport<?> viewport, float renderDistanceBlocks, int chunkCount) {
        viewport.depthBoundingBuffer.clear(this.properties.inverseClearDepth());
        if (chunkCount == 0) {
            return;
        }

        long ptr = UploadStream.INSTANCE.upload(this.uniformBuffer, 0, 128);
        long matPtr = ptr; ptr += 4*4*4;

        {//This is recomputed to be in chunk section space not worldsection

            //Camera block pos
            int bx = (int)Math.floor(viewport.cameraX);
            int by = (int)Math.floor(viewport.cameraY);
            int bz = (int)Math.floor(viewport.cameraZ);
            new Vector3i(bx, by, bz).getToAddress(ptr); ptr += 4*4;

            var negInnerBlock = new Vector3f(
                    (float) (viewport.cameraX - bx),
                    (float) (viewport.cameraY - by),
                    (float) (viewport.cameraZ - bz));


            negInnerBlock.getToAddress(ptr); ptr += 4*3;
            viewport.MVP.translate(negInnerBlock.negate(), new Matrix4f()).getToAddress(matPtr);
            MemoryUtil.memPutFloat(ptr, renderDistanceBlocks); ptr += 4;
        }
        UploadStream.INSTANCE.commit();


        {
            //need to reverse the winding order since we want the back faces of the AABB, not the front

            glFrontFace(GL_CW);//Reverse winding order

            //"reverse depth buffer" it goes from 0->1 where 1 is far away
            glEnable(GL_CULL_FACE);
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(this.properties.furtherDepthCompare());
        }

        glBindVertexArray(GlVertexArray.STATIC_VAO);
        viewport.depthBoundingBuffer.bind();
        this.rasterShader.bind();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, SharedIndexBuffer.INSTANCE_BB_BYTE.id());
        if (this.pipeline != null) this.pipeline.bindUniforms();//shader TAA

        //Batch the draws into groups of size 32
        int count = chunkCount;
        if (count >= 32) {
            glDrawElementsInstanced(GL_TRIANGLES, 6 * 2 * 3 * 32, GL_UNSIGNED_BYTE, 0, count/32);
        }
        if (count%32 != 0) {
            glDrawElementsInstancedBaseInstance(GL_TRIANGLES, 6 * 2 * 3 * (count%32), GL_UNSIGNED_BYTE, 0, 1, (count/32)*32);
        }

        {
            glFrontFace(GL_CCW);//Restore winding order

            glDepthFunc(this.properties.closerEqualDepthCompare());

            //TODO: check this is correct
            glEnable(GL_CULL_FACE);
            glEnable(GL_DEPTH_TEST);
        }
    }

    private void _remPos(long pos) {
        int idx = this.chunk2idx.remove(pos);
        if (idx == -1) {
            Logger.warn("Chunk not in map: " + pos);
            return;
        }
        if (idx == this.chunk2idx.size()) {
            //Dont need to do anything as heap is already compact
            return;
        }
        if (this.idx2chunk[idx] != pos) {
            throw new IllegalStateException();
        }

        //Move last entry on heap to this index
        long ePos = this.idx2chunk[this.chunk2idx.size()];// since is already removed size is correct end idx
        if (this.chunk2idx.put(ePos, idx) == -1) {
            throw new IllegalStateException();
        }
        this.idx2chunk[idx] = ePos;

        //Put the end pos into the new idx
        this.put(idx, ePos);
    }

    private void _addPos(long pos) {
        if (this.chunk2idx.containsKey(pos)) {
            Logger.warn("Chunk already in map: " + pos);
            return;
        }
        this.ensureSize1();//Resize if needed

        int idx = this.chunk2idx.size();
        this.chunk2idx.put(pos, idx);
        this.idx2chunk[idx] = pos;

        this.put(idx, pos);
    }

    private void ensureSize1() {
        if (this.chunk2idx.size() < this.idx2chunk.length) return;
        //Commit any copies, ensures is synced to new buffer
        UploadStream.INSTANCE.commit();

        int size = (int) (this.idx2chunk.length*1.5);
        Logger.info("Resizing chunk position buffer to: " + size);
        //Need to resize
        var old = this.chunkPosBuffer;
        this.chunkPosBuffer = new GlBuffer(size * 8L);
        glCopyNamedBufferSubData(old.id, this.chunkPosBuffer.id, 0, 0, old.size());
        old.free();
        var old2 = this.idx2chunk;
        this.idx2chunk = new long[size];
        System.arraycopy(old2, 0, this.idx2chunk, 0, old2.length);
        //Replace the old buffer with the new one
        ((AutoBindingShader)this.rasterShader).ssbo(1, this.chunkPosBuffer);
    }

    private void put(int idx, long pos) {
        long ptr2 = UploadStream.INSTANCE.upload(this.chunkPosBuffer, 8L*idx, 8);
        //Need to do it in 2 parts because ivec2 is 2 parts
        putPos(ptr2, pos);
    }
    private static void putPos(long ptr, long pos) {
        MemoryUtil.memPutInt(ptr, (int)(pos&0xFFFFFFFFL)); ptr += 4;
        MemoryUtil.memPutInt(ptr, (int)((pos>>>32)&0xFFFFFFFFL));
    }

    public void reset() {
        this.chunk2idx.clear();
    }

    public void free() {
        this.rasterShader.free();
        this.uniformBuffer.free();
        this.chunkPosBuffer.free();
    }







    private static int findEmitBoundingChunks(Viewport<?> viewport, float searchDistance, int capacity, long writePtr) {
        if (capacity == -1) {
            writePtr = 0;
            capacity = Integer.MAX_VALUE;
        }
        //WTAF IS THIS HORRIFIC CODE, _screams_ this is so so bad, and jank and slow and orrible but
        // headache hard think
        float d2 = searchDistance*searchDistance;
        int bx = (int)Math.floor(viewport.cameraX);
        int by = (int)Math.floor(viewport.cameraY);
        int bz = (int)Math.floor(viewport.cameraZ);
        float fy = (float) (viewport.cameraY - (int)viewport.cameraY);
        float fx = (float) (viewport.cameraX - (int)viewport.cameraX);
        float fz = (float) (viewport.cameraZ - (int)viewport.cameraZ);


        //Inclusive
        int mincy = by>>4;
        int maxcy = by>>4;
        {
            while (testYPos(by, fy, mincy, searchDistance)) {
                mincy--;
            }
            mincy++;
            while (testYPos(by, fy, maxcy, searchDistance)) {
                maxcy++;
            }
            maxcy--;
        }

        int count = 0;

        final long bpos = Integer.toUnsignedLong(bx)|(Integer.toUnsignedLong(bz)<<32);
        int minsx = ((int)Math.floor(viewport.cameraX-searchDistance)>>4)-2;
        int maxsx = ((int)Math.ceil(viewport.cameraX+searchDistance)>>4)+2;
        int minsz = ((int)Math.floor(viewport.cameraZ-searchDistance)>>4)-2;
        int maxsz = ((int)Math.ceil(viewport.cameraZ+searchDistance)>>4)+2;
        for (int cx = minsx; cx<maxsx; cx++) {
            for (int cz = minsz; cz<maxsz; cz++) {
                if (testXZPos(bpos, fx, fz, cx, cz, d2)) {
                    //Corner exposed
                    if ((!testXZPos(bpos, fx, fz, cx+1, cz+1, d2))||
                            (!testXZPos(bpos, fx, fz, cx-1, cz+1, d2))||
                            (!testXZPos(bpos, fx, fz, cx+1, cz-1, d2))||
                            (!testXZPos(bpos, fx, fz, cx-1, cz-1, d2))) {
                        //Emit column
                        for (int cy = mincy+1; cy<maxcy; cy++) {
                            if (count++<capacity && writePtr != 0) {
                                putPos(writePtr, SectionPos.asLong(cx,cy,cz));
                                writePtr += 8;
                            }
                        }
                    }
                    if (maxcy!=mincy) {
                        //Emit 2 points at mincy, maxcy (assuming that mincy!=maxcy)
                        if (count++<capacity && writePtr != 0) {
                            putPos(writePtr, SectionPos.asLong(cx,maxcy,cz));
                            writePtr += 8;
                        }
                        if (count++<capacity && writePtr != 0) {
                            putPos(writePtr, SectionPos.asLong(cx,mincy,cz));
                            writePtr += 8;
                        }
                    } else {
                        //emit 1 point
                        if (count++<capacity && writePtr != 0) {
                            putPos(writePtr, SectionPos.asLong(cx,mincy,cz));
                            writePtr += 8;
                        }
                    }
                }
            }
        }
        if (count>capacity) {
            return -count;
        }
        return count;
    }

    private static boolean testYPos(int by, float fy, int cy, float d) {
        int ry =  cy*16-by;
        float dy = (float)nearestToZero(ry - 1, ry + 17) - fy;
        return Math.abs(dy) < d;
    }

    private static boolean testXZPos(long bpos, float fx, float fz, int cx, int cy, float d2) {
        int rx =  cx*16-((int)bpos);
        int rz =  cy*16-((int)(bpos>>32));
        float dx = (float)nearestToZero(rx - 1, rx + 17) - fx;
        float dz = (float)nearestToZero(rz - 1, rz + 17) - fz;
        return dx * dx + dz * dz < d2;
    }

    private static int nearestToZero(int min, int max) {
        int clamped = 0;
        if (min > 0) {
            clamped = min;
        }

        if (max < 0) {
            clamped = max;
        }

        return clamped;
    }
}
