package me.cortex.voxy.client.core.rendering;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.GlFramebuffer;
import me.cortex.voxy.client.core.gl.GlTexture;
import me.cortex.voxy.client.core.gl.shader.AutoBindingShader;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.rendering.util.SharedIndexBuffer;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.common.Logger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
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
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL31.glDrawElementsInstanced;
import static org.lwjgl.opengl.GL42.glDrawElementsInstancedBaseInstance;
import static org.lwjgl.opengl.GL45.glClearNamedFramebufferfv;

//This is a render subsystem, its very simple in what it does
// it renders an AABB around loaded chunks, thats it
public class ChunkBoundRenderer {
    private static final int INIT_MAX_CHUNK_COUNT = 1<<12;
    private GlBuffer chunkPosBuffer = new GlBuffer(INIT_MAX_CHUNK_COUNT*8);//Stored as ivec2
    private final GlBuffer uniformBuffer = new GlBuffer(128);
    private final Long2IntOpenHashMap chunk2idx = new Long2IntOpenHashMap(INIT_MAX_CHUNK_COUNT);
    private long[] idx2chunk = new long[INIT_MAX_CHUNK_COUNT];
    private final Shader rasterShader = Shader.makeAuto()
            .add(ShaderType.VERTEX, "voxy:chunkoutline/outline.vsh")
            .add(ShaderType.FRAGMENT, "voxy:chunkoutline/outline.fsh")
            .compile()
            .ubo(0, this.uniformBuffer)
            .ssbo(1, this.chunkPosBuffer);

    private GlTexture depthBuffer = new GlTexture().store(GL_DEPTH_COMPONENT24, 1, 128, 128);
    private final GlFramebuffer frameBuffer = new GlFramebuffer().bind(GL_DEPTH_ATTACHMENT, this.depthBuffer).verify();
    private final LongOpenHashSet addQueue = new LongOpenHashSet();
    private final LongOpenHashSet remQueue = new LongOpenHashSet();

    public ChunkBoundRenderer() {
        this.chunk2idx.defaultReturnValue(-1);
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
    public void render(Viewport<?> viewport) {
        if (!this.remQueue.isEmpty()) {
            boolean wasEmpty = this.chunk2idx.isEmpty();
            this.remQueue.forEach(this::_remPos);//TODO: REPLACE WITH SCATTER COMPUTE
            this.remQueue.clear();
            if (this.chunk2idx.isEmpty()&&!wasEmpty) {//When going from stuff to nothing need to clear the depth buffer
                glClearNamedFramebufferfv(this.frameBuffer.id, GL_DEPTH, 0, new float[]{0});
            }
        }

        if (this.depthBuffer.getWidth() != viewport.width || this.depthBuffer.getHeight() != viewport.height) {
            this.depthBuffer.free();
            this.depthBuffer = new GlTexture().store(GL_DEPTH_COMPONENT24, 1, viewport.width, viewport.height);
            this.frameBuffer.bind(GL_DEPTH_ATTACHMENT, this.depthBuffer).verify();
        }

        if (this.chunk2idx.isEmpty() && this.addQueue.isEmpty()) return;

        long ptr = UploadStream.INSTANCE.upload(this.uniformBuffer, 0, 128);
        long matPtr = ptr; ptr += 4*4*4;

        final float renderDistance = MinecraftClient.getInstance().options.getClampedViewDistance()*16;//In blocks

        {//This is recomputed to be in chunk section space not worldsection
            int sx = MathHelper.floor(viewport.cameraX) >> 4;
            int sy = MathHelper.floor(viewport.cameraY) >> 4;
            int sz = MathHelper.floor(viewport.cameraZ) >> 4;
            new Vector3i(sx, sy, sz).getToAddress(ptr); ptr += 4*4;

            var negInnerSec = new Vector3f(
                    -(float) (viewport.cameraX - (sx << 4)),
                    -(float) (viewport.cameraY - (sy << 4)),
                    -(float) (viewport.cameraZ - (sz << 4)));

            viewport.MVP.translate(negInnerSec, new Matrix4f()).getToAddress(matPtr);

            negInnerSec.getToAddress(ptr); ptr += 4*3;
            MemoryUtil.memPutFloat(ptr, renderDistance); ptr += 4;
        }
        UploadStream.INSTANCE.commit();


        {
            //need to reverse the winding order since we want the back faces of the AABB, not the front

            glFrontFace(GL_CW);//Reverse winding order

            //"reverse depth buffer" it goes from 0->1 where 1 is far away
            glClearNamedFramebufferfv(this.frameBuffer.id, GL_DEPTH, 0, new float[]{0});
            glEnable(GL_CULL_FACE);
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_GREATER);
        }

        glBindVertexArray(RenderService.STATIC_VAO);
        glBindFramebuffer(GL_FRAMEBUFFER, this.frameBuffer.id);
        this.rasterShader.bind();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, SharedIndexBuffer.INSTANCE_BB_BYTE.id());

        //Batch the draws into groups of size 32
        int count = this.chunk2idx.size();
        if (count > 32) {
            glDrawElementsInstanced(GL_TRIANGLES, 6 * 2 * 3 * 32, GL_UNSIGNED_BYTE, 0, count/32);
        }
        if (count%32 != 0) {
            glDrawElementsInstancedBaseInstance(GL_TRIANGLES, 6 * 2 * 3 * (count%32), GL_UNSIGNED_BYTE, 0, 1, (count/32)*32);
        }

        {
            glFrontFace(GL_CCW);//Restore winding order

            glDepthFunc(GL_LEQUAL);

            //TODO: check this is correct
            glEnable(GL_CULL_FACE);
            glEnable(GL_DEPTH_TEST);
        }


        if (!this.addQueue.isEmpty()) {
            this.addQueue.forEach(this::_addPos);//TODO: REPLACE WITH SCATTER COMPUTE
            this.addQueue.clear();
            UploadStream.INSTANCE.commit();
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
        MemoryUtil.memPutInt(ptr2, (int)(pos&0xFFFFFFFFL)); ptr2 += 4;
        MemoryUtil.memPutInt(ptr2, (int)((pos>>>32)&0xFFFFFFFFL));
    }

    public void reset() {
        this.chunk2idx.clear();
    }

    public void free() {
        this.depthBuffer.free();
        this.frameBuffer.free();

        this.rasterShader.free();
        this.uniformBuffer.free();
        this.chunkPosBuffer.free();
    }

    public GlTexture getDepthBoundTexture() {
        return this.depthBuffer;
    }
}
