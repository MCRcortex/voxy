package me.cortex.neovoxy.client.core.model.bakery;


import me.cortex.neovoxy.common.util.MemoryBuffer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.lwjgl.system.MemoryUtil;

import static me.cortex.neovoxy.client.core.model.bakery.BudgetBufferRenderer.VERTEX_FORMAT_SIZE;

import com.mojang.blaze3d.vertex.VertexConsumer;

public final class ReuseVertexConsumer implements VertexConsumer {
    private MemoryBuffer buffer = new MemoryBuffer(8192);
    private long ptr;
    private int count;
    private int defaultMeta;

    public boolean anyShaded;
    public boolean anyDarkendTex;

    public ReuseVertexConsumer() {
        this.reset();
    }

    public ReuseVertexConsumer setDefaultMeta(int meta) {
        this.defaultMeta = meta;
        return this;
    }

    @Override
    public ReuseVertexConsumer addVertex(float x, float y, float z) {
        this.ensureCanPut();
        this.ptr += VERTEX_FORMAT_SIZE; this.count++; //Goto next vertex
        this.meta(this.defaultMeta);
        MemoryUtil.memPutFloat(this.ptr, x);
        MemoryUtil.memPutFloat(this.ptr + 4, y);
        MemoryUtil.memPutFloat(this.ptr + 8, z);
        return this;
    }

    public ReuseVertexConsumer meta(int metadata) {
        MemoryUtil.memPutInt(this.ptr + 12, metadata);
        return this;
    }

    @Override
    public ReuseVertexConsumer setColor(int red, int green, int blue, int alpha) {
        return this;
    }

    @Override
    public VertexConsumer setColor(int i) {
        return this;
    }

    @Override
    public ReuseVertexConsumer setUv(float u, float v) {
        MemoryUtil.memPutFloat(this.ptr + 16, u);
        MemoryUtil.memPutFloat(this.ptr + 20, v);
        return this;
    }

    @Override
    public ReuseVertexConsumer setUv1(int u, int v) {
        return this;
    }

    @Override
    public ReuseVertexConsumer setUv2(int u, int v) {
        return this;
    }

    @Override
    public ReuseVertexConsumer setNormal(float x, float y, float z) {
        return this;
    }

    // MC 1.21.1: setLineWidth() removed from VertexConsumer interface
    public VertexConsumer setLineWidth(float f) {
        return null;
    }

    public ReuseVertexConsumer quad(BakedQuad quad, int metadata) {
        // MC 1.21.1: BakedQuad API changed - shade() → isShade(), sprite() → getSprite()
        this.anyShaded |= quad.isShade();
        // MC 1.21.1: MipmapStrategy check removed - darkened textures not detected
        this.anyDarkendTex = false;
        this.ensureCanPut();

        // MC 1.21.1: Extract vertex data from int[] vertices array
        // BLOCK format: 8 ints per vertex (pos xyz, color, uv0 xy, uv2, normal+pad)
        int[] vertices = quad.getVertices();
        for (int i = 0; i < 4; i++) {
            int base = i * 8;
            // Position: ints 0-2 are float bits
            float x = Float.intBitsToFloat(vertices[base]);
            float y = Float.intBitsToFloat(vertices[base + 1]);
            float z = Float.intBitsToFloat(vertices[base + 2]);
            // UV0: ints 4-5 are float bits
            float u = Float.intBitsToFloat(vertices[base + 4]);
            float v = Float.intBitsToFloat(vertices[base + 5]);

            this.addVertex(x, y, z);
            this.setUv(u, v);
            this.meta(metadata);
        }
        return this;
    }

    private void ensureCanPut() {
        if ((long) (this.count + 5) * VERTEX_FORMAT_SIZE < this.buffer.size) {
            return;
        }
        long offset = this.ptr-this.buffer.address;
        //1.5x the size
        var newBuffer = new MemoryBuffer((((int)(this.buffer.size*2)+VERTEX_FORMAT_SIZE-1)/VERTEX_FORMAT_SIZE)*VERTEX_FORMAT_SIZE);
        this.buffer.cpyTo(newBuffer.address);
        this.buffer.free();
        this.buffer = newBuffer;
        this.ptr = offset + newBuffer.address;
    }

    public ReuseVertexConsumer reset() {
        this.anyShaded = false;
        this.anyDarkendTex = false;
        this.defaultMeta = 0;//RESET THE DEFAULT META
        this.count = 0;
        this.ptr = this.buffer.address - VERTEX_FORMAT_SIZE;//the thing is first time this gets incremented by FORMAT_STRIDE
        return this;
    }

    public void free() {
        this.ptr = 0;
        this.count = 0;
        this.buffer.free();
        this.buffer = null;
    }

    public boolean isEmpty() {
        return this.count == 0;
    }

    public int quadCount() {
        if (this.count%4 != 0) throw new IllegalStateException();
        return this.count/4;
    }

    public long getAddress() {
        return this.buffer.address;
    }
}
