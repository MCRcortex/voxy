package me.cortex.voxy.client.core.gpu;

/**
 * Abstraction over a GPU framebuffer (render target).
 */
public interface IGpuFramebuffer extends IGpuResource {
    int id();

    IGpuFramebuffer bind(int attachment, IGpuTexture texture);
    IGpuFramebuffer bind(int attachment, IGpuTexture texture, int level);
    IGpuFramebuffer bind(int attachment, IGpuRenderBuffer buffer);
    IGpuFramebuffer setDrawBuffers(int... buffers);
    IGpuFramebuffer verify();
    IGpuFramebuffer name(String name);
}
