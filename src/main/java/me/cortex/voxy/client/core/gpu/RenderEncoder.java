package me.cortex.voxy.client.core.gpu;

/**
 * In-flight encoder for a render pass.
 *
 * Created by {@link RenderBackend#beginRenderPass(RenderPassDesc)} and closed
 * via {@link #close()} (which ends encoding on Metal/Vulkan and unbinds the
 * framebuffer on OpenGL).
 *
 * Initial M2/M3 surface is intentionally minimal — clear-via-load-action only.
 * Pipeline binding, draw calls, and resource binding land in M5+.
 */
public interface RenderEncoder extends AutoCloseable {
    @Override
    void close();
}
