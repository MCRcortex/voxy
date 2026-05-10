package me.cortex.voxy.client.core.gpu;

/**
 * In-flight encoder for a compute pass.
 *
 * Created by {@link RenderBackend#beginComputePass()} and closed via
 * {@link #close()} (which ends encoding on Metal/Vulkan).
 *
 * M7 surface covers what Voxy's compute pipeline needs at minimum:
 *   - Pipeline binding
 *   - SSBO/UBO buffer binding (Voxy's main mechanism for compute data layout)
 *   - Group-count dispatch
 *
 * Texture binding and indirect dispatch land in M8/M9 alongside the
 * {@code HiZBuffer2} and traversal migrations.
 */
public interface ComputeEncoder extends AutoCloseable {

    /** Bind a compute pipeline state object. All subsequent dispatches use it. */
    void setPipeline(IGpuPipeline pipeline);

    /**
     * Bind a buffer at the given binding index in the active shader's binding
     * set 0. Voxy's GLSL uses {@code layout(binding = N) buffer ...}; we map
     * those bindings 1:1 across backends.
     */
    void setBuffer(int binding, IGpuBuffer buffer, long offset);

    /**
     * Dispatch (groupCountX × groupCountY × groupCountZ) thread-groups. The
     * thread-group size itself comes from the bound pipeline (declared as
     * {@code layout(local_size_x=...)} in the shader).
     */
    void dispatch(int groupCountX, int groupCountY, int groupCountZ);

    @Override
    void close();
}
