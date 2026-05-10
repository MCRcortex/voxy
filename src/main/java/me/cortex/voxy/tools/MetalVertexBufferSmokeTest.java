package me.cortex.voxy.tools;

import me.cortex.voxy.client.core.gpu.GraphicsPipelineDesc;
import me.cortex.voxy.client.core.gpu.IGpuBuffer;
import me.cortex.voxy.client.core.gpu.IGpuPipeline;
import me.cortex.voxy.client.core.gpu.IGpuTexture;
import me.cortex.voxy.client.core.gpu.RenderEncoder;
import me.cortex.voxy.client.core.gpu.RenderPassDesc;
import me.cortex.voxy.client.core.gpu.VertexLayout;
import me.cortex.voxy.client.core.gpu.shader.RuntimeShaderCompiler;
import me.cortex.voxy.client.core.metal.MetalBuffer;
import me.cortex.voxy.client.core.metal.MetalNative;
import me.cortex.voxy.client.core.metal.MetalRenderBackend;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Validation for the encoder-API expansion:
 *   - {@link VertexLayout} → MTLVertexDescriptor wiring inside
 *     {@code createGraphicsPipeline}.
 *   - {@link RenderEncoder#bindVertexBuffer} feeds the vertex shader's
 *     {@code in} attributes (instead of the gl_VertexIndex shortcut M5
 *     used).
 *   - {@link RenderEncoder#drawIndirect} reads draw parameters from a
 *     buffer rather than from method arguments — the same indirect call
 *     shape MDICSectionRenderer migration will use post-M9.
 *
 * Renders a colored triangle by sourcing per-vertex pos+color from an
 * RGBA-packed vertex buffer (3 vertices × 20 bytes), with the vertex
 * count + base instance coming from a {@code VkDrawIndirectCommand}-shaped
 * 16-byte indirect buffer. Verifies the corners stay clear and the
 * interior was rasterized.
 *
 * Run with {@code ./gradlew testMetalVertexBuffer}.
 */
public final class MetalVertexBufferSmokeTest {

    private static final int W = 256;
    private static final int H = 256;
    private static final int GL_RGBA8 = 0x8058;
    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int VERTEX_STRIDE = 20; // 2*float pos + 3*float color = 8 + 12

    private MetalVertexBufferSmokeTest() {}

    public static void main(String[] args) throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (!(os.contains("mac") && arch.contains("aarch64"))) {
            System.err.println("M9-prep smoke test requires macOS aarch64 (got " + os + "/" + arch + ")");
            System.exit(2);
        }
        if (!MetalNative.load()) {
            System.err.println("Metal native library failed to load");
            System.exit(2);
        }

        Path shadersRoot = Path.of("src/main/resources/assets/voxy/shaders/tools").toAbsolutePath();
        String vertGlsl = Files.readString(shadersRoot.resolve("triangle_vbuf.vert"), StandardCharsets.UTF_8);
        String fragGlsl = Files.readString(shadersRoot.resolve("triangle.frag"), StandardCharsets.UTF_8);

        RuntimeShaderCompiler.Result vert = RuntimeShaderCompiler.compile(vertGlsl,
                RuntimeShaderCompiler.Stage.VERTEX, Map.of(),
                RuntimeShaderCompiler.Target.METAL_MSL);
        RuntimeShaderCompiler.Result frag = RuntimeShaderCompiler.compile(fragGlsl,
                RuntimeShaderCompiler.Stage.FRAGMENT, Map.of(),
                RuntimeShaderCompiler.Target.METAL_MSL);
        System.out.println("Compiled triangle_vbuf.vert -> " + vert.spirv().length + " B SPIRV / "
                + vert.mslSource().length() + " ch MSL");
        System.out.println("Compiled triangle.frag      -> " + frag.spirv().length + " B SPIRV / "
                + frag.mslSource().length() + " ch MSL");

        VertexLayout layout = VertexLayout.builder()
                .buffer(0, VERTEX_STRIDE, VertexLayout.StepRate.PER_VERTEX)
                .attribute(0, VertexLayout.VertexFormat.FLOAT2, 0, 0)   // inPos
                .attribute(1, VertexLayout.VertexFormat.FLOAT3, 8, 0)   // inColor
                .build();

        MetalRenderBackend backend = new MetalRenderBackend();
        IGpuPipeline pipeline = null;
        IGpuBuffer vertexBuf = null;
        IGpuBuffer indirectBuf = null;
        try {
            IGpuTexture target = backend.createTexture(GL_TEXTURE_2D);
            target.store(GL_RGBA8, 1, W, H);
            target.name("voxy-m9prep-target");

            // Vertex buffer: 3 vertices × (vec2 pos + vec3 color)
            vertexBuf = backend.createBuffer(3L * VERTEX_STRIDE);
            ByteBuffer vb = MemoryUtil.memByteBuffer(((MetalBuffer) vertexBuf).getContentsPtr(),
                    3 * VERTEX_STRIDE).order(ByteOrder.LITTLE_ENDIAN);
            // v0: bottom-left, red
            vb.putFloat(-0.6f).putFloat(-0.5f).putFloat(1.0f).putFloat(0.2f).putFloat(0.2f);
            // v1: bottom-right, green
            vb.putFloat( 0.6f).putFloat(-0.5f).putFloat(0.2f).putFloat(1.0f).putFloat(0.2f);
            // v2: top, blue
            vb.putFloat( 0.0f).putFloat( 0.7f).putFloat(0.2f).putFloat(0.2f).putFloat(1.0f);

            // Indirect buffer holds one VkDrawIndirectCommand:
            //   (vertexCount, instanceCount, firstVertex, firstInstance)
            indirectBuf = backend.createBuffer(16);
            ByteBuffer ib = MemoryUtil.memByteBuffer(((MetalBuffer) indirectBuf).getContentsPtr(), 16)
                    .order(ByteOrder.LITTLE_ENDIAN);
            ib.putInt(3).putInt(1).putInt(0).putInt(0);

            pipeline = backend.createGraphicsPipeline(new GraphicsPipelineDesc(
                    vert.mslSource(), frag.mslSource(),
                    vert.spirv(), frag.spirv(),
                    GL_RGBA8, layout, "voxy:tools/triangle-vbuf"));

            RenderPassDesc pass = RenderPassDesc.builder(W, H)
                    .clearColor(target, 0.1f, 0.1f, 0.15f, 1.0f)
                    .build();

            try (RenderEncoder enc = backend.beginRenderPass(pass)) {
                enc.setPipeline(pipeline);
                enc.setViewport(0, 0, W, H, 0, 1);
                enc.setScissor(0, 0, W, H);
                enc.bindVertexBuffer(0, vertexBuf, 0);
                enc.drawIndirect(RenderEncoder.PRIMITIVE_TRIANGLES, indirectBuf, 0, 1, 16);
            }
            backend.submit();

            byte[] pixels = backend.readPixelsRGBA8(target, 0, 0, W, H);

            int clearPacked = packRgba(0.1f, 0.1f, 0.15f, 1.0f);
            int topLeft = sampleRgba(pixels, W, 4, 4);
            int botRight = sampleRgba(pixels, W, W - 4, H - 4);
            int interior = sampleRgba(pixels, W, W / 2, H * 5 / 8);

            System.out.printf("Top-left pixel: 0x%08X (clear=0x%08X) %s%n",
                    topLeft, clearPacked, topLeft == clearPacked ? "OK" : "MISMATCH");
            System.out.printf("Bot-right pixel: 0x%08X (clear=0x%08X) %s%n",
                    botRight, clearPacked, botRight == clearPacked ? "OK" : "MISMATCH");
            System.out.printf("Interior pixel: 0x%08X (clear=0x%08X) %s%n",
                    interior, clearPacked, interior != clearPacked ? "DIFFERENT (triangle drew)" : "STILL CLEAR");

            if (topLeft != clearPacked || botRight != clearPacked) {
                throw new RuntimeException("Background corner pixels changed — clear path may be broken");
            }
            if (interior == clearPacked) {
                throw new RuntimeException("Triangle interior unchanged — VertexLayout/bindVertexBuffer/drawIndirect didn't reach the rasterizer");
            }

            System.out.println();
            System.out.println("M9-prep SMOKE OK — VertexLayout + bindVertexBuffer + drawIndirect on Apple M4 Max");
        } finally {
            if (pipeline != null) pipeline.close();
            backend.shutdown();
        }
    }

    private static int packRgba(float r, float g, float b, float a) {
        return (clamp8(r) << 24) | (clamp8(g) << 16) | (clamp8(b) << 8) | clamp8(a);
    }

    private static int clamp8(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255.0f)));
    }

    private static int sampleRgba(byte[] pixels, int width, int x, int y) {
        int offset = (y * width + x) * 4;
        return ((pixels[offset] & 0xFF) << 24)
                | ((pixels[offset + 1] & 0xFF) << 16)
                | ((pixels[offset + 2] & 0xFF) << 8)
                | (pixels[offset + 3] & 0xFF);
    }
}
