package me.cortex.voxy.tools;

import me.cortex.voxy.client.core.gpu.GraphicsPipelineDesc;
import me.cortex.voxy.client.core.gpu.IGpuBuffer;
import me.cortex.voxy.client.core.gpu.IGpuIndirectCommandBuffer;
import me.cortex.voxy.client.core.gpu.IGpuPipeline;
import me.cortex.voxy.client.core.gpu.IGpuTexture;
import me.cortex.voxy.client.core.gpu.RenderEncoder;
import me.cortex.voxy.client.core.gpu.RenderPassDesc;
import me.cortex.voxy.client.core.gpu.VertexLayout;
import me.cortex.voxy.client.core.gpu.shader.RuntimeShaderCompiler;
import me.cortex.voxy.client.core.metal.MetalBuffer;
import me.cortex.voxy.client.core.metal.MetalHandleMap;
import me.cortex.voxy.client.core.metal.MetalIndirectCommandBuffer;
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
 * Blocker 1 validation: MTLIndirectCommandBuffer + executeCommandsInBuffer.
 *
 * Builds a 2-slot ICB, CPU-populates slot 0 with an indexed-triangle draw,
 * then renders by encoding {@code executeCommandsInBuffer:} with a range
 * buffer holding {@code (location=0, length=1)}. Successfully drawing
 * proves:
 *   1. {@code mtlDeviceNewIndirectCommandBuffer} produces a usable ICB.
 *   2. {@code mtlIndirectRenderCommandSetVertexBuffer} +
 *      {@code DrawIndexedPrimitives} populates a slot.
 *   3. {@code mtlRenderEncoderExecuteCommandsInBuffer} reads the (loc, len)
 *      pair from a Metal buffer at encode time and runs the slot.
 *
 * This is the Metal-side primitive MDICSectionRenderer needs to replace
 * {@code glMultiDrawElementsIndirectCountARB} when the GPU-resident
 * count buffer reaches Metal. Run with {@code ./gradlew testMetalIcb}.
 */
public final class MetalIcbSmokeTest {

    private static final int W = 256;
    private static final int H = 256;
    private static final int GL_RGBA8 = 0x8058;
    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int VERTEX_STRIDE = 20; // 2*float pos + 3*float color

    /** MTLIndexType.UInt16 raw value. */
    private static final int MTL_INDEX_UINT16 = 0;
    /** MTLPrimitiveType.Triangle raw value. */
    private static final int MTL_PRIMITIVE_TRIANGLE = 3;

    private MetalIcbSmokeTest() {}

    public static void main(String[] args) throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (!(os.contains("mac") && arch.contains("aarch64"))) {
            System.err.println("ICB smoke test requires macOS aarch64 (got " + os + "/" + arch + ")");
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

        VertexLayout layout = VertexLayout.builder()
                .buffer(0, VERTEX_STRIDE, VertexLayout.StepRate.PER_VERTEX)
                .attribute(0, VertexLayout.VertexFormat.FLOAT2, 0, 0)
                .attribute(1, VertexLayout.VertexFormat.FLOAT3, 8, 0)
                .build();

        MetalRenderBackend backend = new MetalRenderBackend();
        IGpuPipeline pipeline = null;
        IGpuBuffer vertexBuf = null;
        IGpuBuffer indexBuf = null;
        IGpuBuffer rangeBuf = null;
        IGpuIndirectCommandBuffer icb = null;
        try {
            IGpuTexture target = backend.createTexture(GL_TEXTURE_2D);
            target.store(GL_RGBA8, 1, W, H);
            target.name("voxy-icb-target");

            // Vertex buffer: 3 vertices × (vec2 pos + vec3 color).
            vertexBuf = backend.createBuffer(3L * VERTEX_STRIDE);
            ByteBuffer vb = MemoryUtil.memByteBuffer(((MetalBuffer) vertexBuf).getContentsPtr(),
                    3 * VERTEX_STRIDE).order(ByteOrder.LITTLE_ENDIAN);
            vb.putFloat(-0.6f).putFloat(-0.5f).putFloat(1.0f).putFloat(0.2f).putFloat(0.2f);
            vb.putFloat( 0.6f).putFloat(-0.5f).putFloat(0.2f).putFloat(1.0f).putFloat(0.2f);
            vb.putFloat( 0.0f).putFloat( 0.7f).putFloat(0.2f).putFloat(0.2f).putFloat(1.0f);

            // Index buffer: 3 uint16 indices for one triangle.
            indexBuf = backend.createBuffer(3L * 2);
            ByteBuffer ib = MemoryUtil.memByteBuffer(((MetalBuffer) indexBuf).getContentsPtr(), 6)
                    .order(ByteOrder.LITTLE_ENDIAN);
            ib.putShort((short) 0).putShort((short) 1).putShort((short) 2);

            // Range buffer: MTLIndirectCommandBufferExecutionRange { uint32 location; uint32 length; }
            // Pad to 16 bytes to match what Voxy's GPU-side range emitters will write.
            rangeBuf = backend.createBuffer(16);
            ByteBuffer rb = MemoryUtil.memByteBuffer(((MetalBuffer) rangeBuf).getContentsPtr(), 16)
                    .order(ByteOrder.LITTLE_ENDIAN);
            rb.putInt(0).putInt(1).putInt(0).putInt(0); // execute commands [0, 1)

            pipeline = backend.createGraphicsPipeline(new GraphicsPipelineDesc(
                    vert.mslSource(), frag.mslSource(),
                    vert.spirv(), frag.spirv(),
                    GL_RGBA8, layout, "voxy:tools/triangle-icb")
                    .withIndirectCommandBufferUsage(true));

            // ICB inheritPipelineState=true requires the MTLRenderPipelineState
            // to have been created with supportIndirectCommandBuffers=YES,
            // which our default pipeline doesn't set. Instead, create the ICB
            // with inheritPipelineState=false and bake the PSO into each
            // command. Same trade-off goes for inheritBuffers — explicit is
            // simpler for the smoke test and matches what MDIC's GPU-side
            // cmdgen will do anyway when it migrates to the ICB model.
            MetalIndirectCommandBuffer mIcb = new MetalIndirectCommandBuffer(
                    ((MetalRenderBackend) backend).device(),
                    2,
                    /*inheritBuffers=*/false,
                    /*inheritPipelineState=*/false);
            icb = mIcb;
            mIcb.name("voxy-icb-smoke");
            mIcb.reset(0, 2);
            if (!mIcb.hasCommand(0)) throw new RuntimeException("ICB slot 0 unreachable");
            long indexBufHandle = MetalHandleMap.getHandle(indexBuf.id());
            long vertexBufHandle = MetalHandleMap.getHandle(vertexBuf.id());
            long psoHandle = ((me.cortex.voxy.client.core.metal.MetalGraphicsPipeline) pipeline).pipelineStateHandle();
            mIcb.encodeSetPipelineState(0, psoHandle);
            // Slot 0 matches the encoder's bindVertexBuffer(0, ...) — after
            // SPIRV-cross transpile, the vertex buffer ends up at [[buffer(0)]].
            mIcb.encodeSetVertexBuffer(0, vertexBufHandle, 0L, 0);
            mIcb.encodeDrawIndexedPrimitives(
                    0,
                    MTL_PRIMITIVE_TRIANGLE,
                    3,                       // indexCount
                    MTL_INDEX_UINT16,
                    indexBufHandle, 0L,      // index buffer + offset
                    1, 0, 0);                // instanceCount, baseVertex, baseInstance

            RenderPassDesc pass = RenderPassDesc.builder(W, H)
                    .clearColor(target, 0.1f, 0.1f, 0.15f, 1.0f)
                    .build();

            try (RenderEncoder enc = backend.beginRenderPass(pass)) {
                enc.setPipeline(pipeline);
                enc.setViewport(0, 0, W, H, 0, 1);
                enc.setScissor(0, 0, W, H);
                enc.bindVertexBuffer(0, vertexBuf, 0);

                // Metal can't track resources the ICB uses indirectly. Declare
                // the index buffer + vertex buffer so executeCommandsInBuffer
                // validates clean. (The pipeline binding is inherited too,
                // but the encoder already knows about it via setPipeline.)
                long encHandle = ((me.cortex.voxy.client.core.metal.MetalRenderEncoder) enc).handle();
                MetalNative.mtlRenderEncoderUseResource(encHandle, indexBufHandle,
                        MetalNative.MTLResourceUsageRead, MetalNative.MTLRenderStageVertex);
                MetalNative.mtlRenderEncoderUseResource(encHandle, vertexBufHandle,
                        MetalNative.MTLResourceUsageRead, MetalNative.MTLRenderStageVertex);

                enc.executeCommandsInBuffer(icb, rangeBuf, 0);
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
                    interior, clearPacked, interior != clearPacked ? "DIFFERENT (ICB drew)" : "STILL CLEAR");

            if (topLeft != clearPacked || botRight != clearPacked) {
                throw new RuntimeException("Background corner pixels changed — clear path may be broken");
            }
            if (interior == clearPacked) {
                throw new RuntimeException("Triangle interior unchanged — ICB executeCommandsInBuffer didn't reach the rasterizer");
            }

            System.out.println();
            System.out.println("Blocker 1 SMOKE OK — MTLIndirectCommandBuffer + executeCommandsInBuffer on Apple Silicon");
        } finally {
            if (icb != null) icb.close();
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
