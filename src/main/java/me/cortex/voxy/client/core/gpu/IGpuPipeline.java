package me.cortex.voxy.client.core.gpu;

/**
 * Backend-agnostic handle for a graphics or compute pipeline state object.
 *
 * Wraps an MTLRenderPipelineState (Metal), VkPipeline (Vulkan), or GL
 * program (OpenGL — backed by glLinkProgram). Pipelines hold shader stages
 * plus all the static state that goes with them (rasterization, depth/stencil,
 * blend, color formats) so they can be bound atomically before a draw.
 *
 * Lifecycle: created via {@link RenderBackend#createGraphicsPipeline} and
 * released via {@link AutoCloseable#close()}.
 */
public interface IGpuPipeline extends AutoCloseable {
    @Override
    void close();
}
