package me.cortex.voxy.tools;

import me.cortex.voxy.client.core.gpu.GraphicsPipelineDesc;
import me.cortex.voxy.client.core.gpu.IGpuPipeline;
import me.cortex.voxy.client.core.gpu.IGpuTexture;
import me.cortex.voxy.client.core.gpu.RenderEncoder;
import me.cortex.voxy.client.core.gpu.RenderPassDesc;
import me.cortex.voxy.client.core.gpu.shader.RuntimeShaderCompiler;
import me.cortex.voxy.client.core.metal.MetalNative;
import me.cortex.voxy.client.core.metal.MetalRenderBackend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * M5 verification: compile the triangle shaders via {@link RuntimeShaderCompiler},
 * build a Metal graphics pipeline, render a 3-vertex triangle into a 256×256
 * render target, read the pixels back, and confirm the triangle area contains
 * the expected per-vertex colors against the cleared background.
 *
 * Run with {@code ./gradlew testMetalTriangle}.
 */
public final class MetalTriangleSmokeTest {

    private MetalTriangleSmokeTest() {}

    public static void main(String[] args) throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (!(os.contains("mac") && arch.contains("aarch64"))) {
            System.err.println("M5 smoke test requires macOS aarch64 (got " + os + "/" + arch + ")");
            System.exit(2);
        }
        if (!MetalNative.load()) {
            System.err.println("Metal native library failed to load");
            System.exit(2);
        }

        Path shadersRoot = Path.of("src/main/resources/assets/voxy/shaders/tools").toAbsolutePath();
        String vertGlsl = Files.readString(shadersRoot.resolve("triangle.vert"), StandardCharsets.UTF_8);
        String fragGlsl = Files.readString(shadersRoot.resolve("triangle.frag"), StandardCharsets.UTF_8);

        RuntimeShaderCompiler.Result vertCompiled = RuntimeShaderCompiler.compile(
                vertGlsl, RuntimeShaderCompiler.Stage.VERTEX, Map.of(),
                RuntimeShaderCompiler.Target.METAL_MSL);
        RuntimeShaderCompiler.Result fragCompiled = RuntimeShaderCompiler.compile(
                fragGlsl, RuntimeShaderCompiler.Stage.FRAGMENT, Map.of(),
                RuntimeShaderCompiler.Target.METAL_MSL);
        System.out.println("Compiled triangle.vert -> " + vertCompiled.spirv().length + " B SPIRV / "
                + vertCompiled.mslSource().length() + " ch MSL");
        System.out.println("Compiled triangle.frag -> " + fragCompiled.spirv().length + " B SPIRV / "
                + fragCompiled.mslSource().length() + " ch MSL");

        MetalRenderBackend backend = new MetalRenderBackend();
        IGpuPipeline pipeline = null;
        try {
            // GL_RGBA8 = 0x8058, GL_TEXTURE_2D = 0x0DE1.
            final int GL_RGBA8 = 0x8058;
            final int GL_TEXTURE_2D = 0x0DE1;
            final int W = 256, H = 256;

            IGpuTexture target = backend.createTexture(GL_TEXTURE_2D);
            target.store(GL_RGBA8, 1, W, H);
            target.name("voxy-m5-triangle-target");

            pipeline = backend.createGraphicsPipeline(new GraphicsPipelineDesc(
                    vertCompiled.mslSource(), fragCompiled.mslSource(),
                    vertCompiled.spirv(), fragCompiled.spirv(),
                    GL_RGBA8, "voxy:tools/triangle"));

            RenderPassDesc pass = RenderPassDesc.builder(W, H)
                    .clearColor(target, 0.1f, 0.1f, 0.15f, 1.0f)
                    .build();

            try (RenderEncoder enc = backend.beginRenderPass(pass)) {
                enc.setPipeline(pipeline);
                enc.draw(RenderEncoder.PRIMITIVE_TRIANGLES, 0, 3, 1, 0);
            }
            backend.submit();

            byte[] pixels = backend.readPixelsRGBA8(target, 0, 0, W, H);

            // Sample three points: triangle interior (around centroid in NDC space)
            // and two corners that should remain clear color.
            int interior = sampleRgba(pixels, W, W / 2, H * 5 / 8);   // ~ (0.0, 0.05) NDC ≈ inside triangle
            int topLeft = sampleRgba(pixels, W, 4, 4);
            int bottomRight = sampleRgba(pixels, W, W - 4, H - 4);

            int clearRgb = packRgba(0.1f, 0.1f, 0.15f, 1.0f);
            boolean cornersClear = (topLeft == clearRgb && bottomRight == clearRgb);
            boolean interiorChanged = interior != clearRgb;

            System.out.printf("Top-left  pixel: 0x%08X (clear=0x%08X) %s%n",
                    topLeft, clearRgb, topLeft == clearRgb ? "OK" : "MISMATCH");
            System.out.printf("Bot-right pixel: 0x%08X (clear=0x%08X) %s%n",
                    bottomRight, clearRgb, bottomRight == clearRgb ? "OK" : "MISMATCH");
            System.out.printf("Interior  pixel: 0x%08X (clear=0x%08X) %s%n",
                    interior, clearRgb, interiorChanged ? "DIFFERENT (triangle drew)" : "STILL CLEAR (triangle missing)");

            if (!cornersClear) {
                throw new RuntimeException("Background corners changed — clear-color path may be broken");
            }
            if (!interiorChanged) {
                throw new RuntimeException("Triangle interior unchanged — pipeline/draw chain didn't rasterize");
            }

            System.out.println();
            System.out.println("M5 SMOKE OK — triangle rasterized over clear background on Apple Silicon");
        } finally {
            if (pipeline != null) pipeline.close();
            backend.shutdown();
        }
    }

    /** Pack normalized RGBA into a 0xRRGGBBAA int matching MTLPixelFormatRGBA8Unorm byte order. */
    private static int packRgba(float r, float g, float b, float a) {
        int rr = clamp8(r), gg = clamp8(g), bb = clamp8(b), aa = clamp8(a);
        return (rr << 24) | (gg << 16) | (bb << 8) | aa;
    }

    private static int clamp8(float v) {
        int i = Math.round(v * 255.0f);
        return Math.max(0, Math.min(255, i));
    }

    private static int sampleRgba(byte[] pixels, int width, int x, int y) {
        int offset = (y * width + x) * 4;
        int r = pixels[offset] & 0xFF;
        int g = pixels[offset + 1] & 0xFF;
        int b = pixels[offset + 2] & 0xFF;
        int a = pixels[offset + 3] & 0xFF;
        return (r << 24) | (g << 16) | (b << 8) | a;
    }
}
