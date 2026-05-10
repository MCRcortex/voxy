package me.cortex.voxy.tools;

import me.cortex.voxy.client.core.gpu.ComputeEncoder;
import me.cortex.voxy.client.core.gpu.ComputePipelineDesc;
import me.cortex.voxy.client.core.gpu.IGpuBuffer;
import me.cortex.voxy.client.core.gpu.IGpuPipeline;
import me.cortex.voxy.client.core.gpu.shader.RuntimeShaderCompiler;
import me.cortex.voxy.client.core.metal.MetalBuffer;
import me.cortex.voxy.client.core.metal.MetalNative;
import me.cortex.voxy.client.core.metal.MetalRenderBackend;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * M7 verification: compile {@code increment.comp}, build a Metal compute
 * pipeline, dispatch a single 64-thread group against a 64-uint SSBO,
 * and confirm every entry got the per-invocation expected value.
 *
 * Run with {@code ./gradlew testMetalCompute}.
 */
public final class MetalComputeSmokeTest {

    private MetalComputeSmokeTest() {}

    public static void main(String[] args) throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (!(os.contains("mac") && arch.contains("aarch64"))) {
            System.err.println("M7 smoke test requires macOS aarch64 (got " + os + "/" + arch + ")");
            System.exit(2);
        }
        if (!MetalNative.load()) {
            System.err.println("Metal native library failed to load");
            System.exit(2);
        }

        Path shadersRoot = Path.of("src/main/resources/assets/voxy/shaders/tools").toAbsolutePath();
        String compGlsl = Files.readString(shadersRoot.resolve("increment.comp"), StandardCharsets.UTF_8);

        RuntimeShaderCompiler.Result compiled = RuntimeShaderCompiler.compile(
                compGlsl, RuntimeShaderCompiler.Stage.COMPUTE, Map.of(),
                RuntimeShaderCompiler.Target.METAL_MSL);
        System.out.println("Compiled increment.comp -> " + compiled.spirv().length + " B SPIRV / "
                + compiled.mslSource().length() + " ch MSL");

        MetalRenderBackend backend = new MetalRenderBackend();
        IGpuPipeline pipeline = null;
        IGpuBuffer ssbo = null;
        try {
            final int N = 64;
            final long bufferSize = (long) N * Integer.BYTES;

            // Voxy's createBuffer doesn't expose storage mode; for the readback
            // smoke test we want CPU-visible memory, so allocate via the Shared
            // path directly. M9 will introduce a backend-agnostic mappable
            // buffer creation API.
            long ssboHandle = MetalNative.mtlDeviceNewBuffer(getDevice(backend), bufferSize,
                    MetalNative.MTLResourceStorageModeShared);
            if (ssboHandle == 0) throw new RuntimeException("Failed to allocate SSBO");

            pipeline = backend.createComputePipeline(new ComputePipelineDesc(
                    compiled.mslSource(), compiled.spirv(),
                    /*localSizeX*/ 64, 1, 1,
                    "voxy:tools/increment"));

            try (ComputeEncoder enc = backend.beginComputePass()) {
                enc.setPipeline(pipeline);
                // We can't go through the IGpuBuffer abstraction here because we
                // bypassed createBuffer; bind directly via JNI for the smoke test.
                MetalNative.mtlComputeEncoderSetBuffer(getEncoderHandle(enc), ssboHandle, 0L, 0);
                enc.dispatch(/*groups*/ 1, 1, 1);
            }
            backend.submit();

            long contents = MetalNative.mtlBufferContents(ssboHandle);
            if (contents == 0) throw new RuntimeException("SSBO contents pointer null");

            ByteBuffer raw = org.lwjgl.system.MemoryUtil.memByteBuffer(contents, (int) bufferSize)
                    .order(ByteOrder.LITTLE_ENDIAN);
            IntBuffer values = raw.asIntBuffer();

            int passed = 0, mismatched = 0;
            for (int i = 0; i < N; i++) {
                int expected = i * 2 + 1;
                int actual = values.get(i);
                if (actual == expected) passed++;
                else {
                    if (mismatched < 4) {
                        System.out.printf("  [%d] expected=%d actual=%d%n", i, expected, actual);
                    }
                    mismatched++;
                }
            }
            MetalNative.mtlRelease(ssboHandle);

            System.out.printf("Compute results: %d/%d match%n", passed, N);
            if (passed != N) {
                throw new RuntimeException("M7 FAILED: " + mismatched + " values mismatched");
            }

            System.out.println();
            System.out.println("M7 SMOKE OK — Metal compute dispatch wrote correct values on Apple Silicon");
        } finally {
            if (pipeline != null) pipeline.close();
            backend.shutdown();
        }
    }

    /**
     * Reach into MetalRenderBackend for its device handle; this is a
     * test-only shortcut because we're bypassing the buffer abstraction.
     */
    private static long getDevice(MetalRenderBackend backend) throws Exception {
        java.lang.reflect.Field f = MetalRenderBackend.class.getDeclaredField("device");
        f.setAccessible(true);
        return (Long) f.get(backend);
    }

    /**
     * Reach into MetalComputeEncoder for its handle; same justification.
     * Production code would route through ComputeEncoder.setBuffer(IGpuBuffer).
     */
    private static long getEncoderHandle(ComputeEncoder enc) throws Exception {
        java.lang.reflect.Field f = enc.getClass().getDeclaredField("encoderHandle");
        f.setAccessible(true);
        return (Long) f.get(enc);
    }
}
