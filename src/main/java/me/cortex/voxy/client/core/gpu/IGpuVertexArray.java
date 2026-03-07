package me.cortex.voxy.client.core.gpu;

/**
 * Abstraction over vertex input layout / vertex array object.
 */
public interface IGpuVertexArray extends IGpuResource {
    int id();
    void bind();
    IGpuVertexArray bindBuffer(int buffer);
    IGpuVertexArray bindElementBuffer(int buffer);
    IGpuVertexArray setStride(int stride);
    IGpuVertexArray setI(int index, int type, int count, int offset);
    IGpuVertexArray setF(int index, int type, int count, int offset);
    IGpuVertexArray setF(int index, int type, int count, boolean normalize, int offset);
}
