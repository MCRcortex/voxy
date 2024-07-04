package me.cortex.voxy.client.core.rendering;

import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.client.core.util.IndexUtil;
import me.cortex.voxy.common.util.MemoryBuffer;
import org.lwjgl.system.MemoryUtil;


//Has a base index buffer of 16380 quads, and also a 1 cube byte index buffer at the end
public class SharedIndexBuffer {
    public static final SharedIndexBuffer INSTANCE = new SharedIndexBuffer();
    public static final SharedIndexBuffer INSTANCE_BYTE = new SharedIndexBuffer(true);

    private final GlBuffer indexBuffer;

    public SharedIndexBuffer() {
        this.indexBuffer = new GlBuffer((1<<16)*6*2 + 6*2*3);
        var quadIndexBuff = IndexUtil.generateQuadIndicesShort(16380);
        var cubeBuff = generateCubeIndexBuffer();

        long ptr = UploadStream.INSTANCE.upload(this.indexBuffer, 0, this.indexBuffer.size());
        quadIndexBuff.cpyTo(ptr);
        cubeBuff.cpyTo((1<<16)*2*6 + ptr);

        quadIndexBuff.free();
        cubeBuff.free();
    }

    private SharedIndexBuffer(boolean type2) {
        this.indexBuffer = new GlBuffer((1<<8)*6 + 6*2*3);
        var quadIndexBuff = IndexUtil.generateQuadIndicesByte(63);
        var cubeBuff = generateCubeIndexBuffer();

        long ptr = UploadStream.INSTANCE.upload(this.indexBuffer, 0, this.indexBuffer.size());
        quadIndexBuff.cpyTo(ptr);
        cubeBuff.cpyTo((1<<8)*6 + ptr);

        quadIndexBuff.free();
        cubeBuff.free();
    }

    private static MemoryBuffer generateCubeIndexBuffer() {
        var buffer = new MemoryBuffer(6*2*3);
        long ptr = buffer.address;
        MemoryUtil.memSet(ptr, 0, 6*2*3 );

        //Bottom face
        MemoryUtil.memPutByte(ptr++, (byte) 0);
        MemoryUtil.memPutByte(ptr++, (byte) 1);
        MemoryUtil.memPutByte(ptr++, (byte) 2);
        MemoryUtil.memPutByte(ptr++, (byte) 3);
        MemoryUtil.memPutByte(ptr++, (byte) 2);
        MemoryUtil.memPutByte(ptr++, (byte) 1);

        //top face
        MemoryUtil.memPutByte(ptr++, (byte) 6);
        MemoryUtil.memPutByte(ptr++, (byte) 5);
        MemoryUtil.memPutByte(ptr++, (byte) 4);
        MemoryUtil.memPutByte(ptr++, (byte) 5);
        MemoryUtil.memPutByte(ptr++, (byte) 6);
        MemoryUtil.memPutByte(ptr++, (byte) 7);

        //north face
        MemoryUtil.memPutByte(ptr++, (byte) 0);
        MemoryUtil.memPutByte(ptr++, (byte) 4);
        MemoryUtil.memPutByte(ptr++, (byte) 1);
        MemoryUtil.memPutByte(ptr++, (byte) 5);
        MemoryUtil.memPutByte(ptr++, (byte) 1);
        MemoryUtil.memPutByte(ptr++, (byte) 4);

        //south face
        MemoryUtil.memPutByte(ptr++, (byte) 3);
        MemoryUtil.memPutByte(ptr++, (byte) 6);
        MemoryUtil.memPutByte(ptr++, (byte) 2);
        MemoryUtil.memPutByte(ptr++, (byte) 6);
        MemoryUtil.memPutByte(ptr++, (byte) 3);
        MemoryUtil.memPutByte(ptr++, (byte) 7);

        //west face
        MemoryUtil.memPutByte(ptr++, (byte) 2);
        MemoryUtil.memPutByte(ptr++, (byte) 4);
        MemoryUtil.memPutByte(ptr++, (byte) 0);
        MemoryUtil.memPutByte(ptr++, (byte) 4);
        MemoryUtil.memPutByte(ptr++, (byte) 2);
        MemoryUtil.memPutByte(ptr++, (byte) 6);

        //east face
        MemoryUtil.memPutByte(ptr++, (byte) 1);
        MemoryUtil.memPutByte(ptr++, (byte) 5);
        MemoryUtil.memPutByte(ptr++, (byte) 3);
        MemoryUtil.memPutByte(ptr++, (byte) 7);
        MemoryUtil.memPutByte(ptr++, (byte) 3);
        MemoryUtil.memPutByte(ptr++, (byte) 5);

        return buffer;
    }

    public int id() {
        return this.indexBuffer.id;
    }
}
