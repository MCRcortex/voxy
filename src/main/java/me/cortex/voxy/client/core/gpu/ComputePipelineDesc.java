package me.cortex.voxy.client.core.gpu;

/**
 * Descriptor for a compute pipeline state object.
 *
 * Carries both Metal (MSL) and Vulkan (SPIRV) representations of the compute
 * shader so the same descriptor works for both backends; produced by a single
 * {@link me.cortex.voxy.client.core.gpu.shader.RuntimeShaderCompiler#compile}
 * call against a {@code .comp} source.
 *
 * The local thread-group size is encoded in the shader via {@code layout(local_size_x=...)}
 * and queried by the backend at pipeline creation time, so callers don't need
 * to repeat it here. {@link ComputeEncoder#dispatch} only needs the number of
 * thread-groups.
 */
public final class ComputePipelineDesc {
    public final String computeMsl;
    public final byte[] computeSpirv;
    /** Original GLSL source — used by the GL backend; Metal/Vulkan ignore. */
    public final String computeGlsl;
    /** Compile-time defines injected before the {@code #version} line. */
    public final java.util.Map<String, String> defines;
    public final int localSizeX;
    public final int localSizeY;
    public final int localSizeZ;
    public final String label;

    public ComputePipelineDesc(String computeMsl, byte[] computeSpirv,
                               int localSizeX, int localSizeY, int localSizeZ,
                               String label) {
        this(null, null, computeMsl, computeSpirv, localSizeX, localSizeY, localSizeZ, label);
    }

    /** Full constructor for M9 migration call sites that have GLSL source. */
    public ComputePipelineDesc(String computeGlsl,
                               java.util.Map<String, String> defines,
                               String computeMsl, byte[] computeSpirv,
                               int localSizeX, int localSizeY, int localSizeZ,
                               String label) {
        this.computeGlsl = computeGlsl;
        this.defines = defines != null ? defines : java.util.Map.of();
        this.computeMsl = computeMsl;
        this.computeSpirv = computeSpirv;
        this.localSizeX = localSizeX;
        this.localSizeY = localSizeY;
        this.localSizeZ = localSizeZ;
        this.label = label;
    }
}
