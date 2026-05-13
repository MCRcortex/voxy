package me.cortex.voxy.client.core.model.bakery;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_UNSIGNED_INT_8_8_8_8_REV;
import static org.lwjgl.opengl.GL30.*;

/**
 * Model texture bakery capture target — uses MC's OpenGL context regardless of
 * Voxy's render backend.
 *
 * The bake pipeline is GL-native: the FBO + colour/depth/metadata textures are
 * allocated via raw GL so they're valid handles inside MC's GL context, even
 * when Voxy's main backend is Metal. {@link #emitToStream} CPU-reads the FBO
 * results with {@code glGetTexImage} (works on Apple's GL 4.1 cap) and packs
 * them into the caller-supplied address in the same layout the legacy
 * {@code bufferreorder.comp} compute shader produced. This replaces the
 * GL 4.3-only compute readback path and removes the dependency on DSA
 * (GL 4.5) framebuffer clears.
 *
 * The caller (ModelFactory.addEntry → ModelTextureBakery.renderToStream)
 * passes {@code destAddr = downloadBuffer.addr() + allocation} so the packed
 * bytes land exactly where {@link me.cortex.voxy.client.core.rendering.util.RawDownloadStream}'s
 * fenced callback will read them.
 */
public class GlViewCapture {
    private final int width;
    private final int height;
    private final int colourTexId;
    private final int depthTexId;
    private final int metaTexId;
    /** Raw GL framebuffer name. Exposed for ModelTextureBakery's glBindFramebuffer. */
    public final int framebufferId;

    // Per-instance persistent scratch buffers for glReadPixels destinations.
    // Allocated once in the constructor, freed in {@link #free()}. Avoids
    // per-invocation malloc/free that may race with deferred Apple GL
    // pixel-pack writes on Apple Silicon (hs_err_pid73671 — SIGBUS in
    // glgVectorCopy on the *second* bake invocation).
    private final long colourScratch;
    private final long depthScratch;
    private final long metaScratch;
    private final long scratchSize;

    /** M13 chunk 1 diagnostic counters — read by AbstractRenderPipeline's Metal-DIAG dump. */
    public static final java.util.concurrent.atomic.AtomicLong DIAG_BAKE_INVOCATIONS = new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong DIAG_BAKE_NONZERO_PIXEL_INVOCATIONS = new java.util.concurrent.atomic.AtomicLong();

    public GlViewCapture(int width, int height) {
        this.width = width;
        this.height = height;

        int totalW = width * 3;
        int totalH = height * 2;

        // Colour attachment (RGBA8).
        this.colourTexId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, this.colourTexId);
        org.lwjgl.opengl.GL42C.glTexStorage2D(GL_TEXTURE_2D, 1, GL_RGBA8, totalW, totalH);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);

        // Metadata attachment (R32UI — alpha/tint flags).
        this.metaTexId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, this.metaTexId);
        org.lwjgl.opengl.GL42C.glTexStorage2D(GL_TEXTURE_2D, 1, GL_R32UI, totalW, totalH);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);

        // Depth+stencil attachment (D24S8). Used as a depth target during
        // bake; read back in {@link #emitToStream} via GL_UNSIGNED_INT_24_8
        // so we get both halves in one read without needing a stencil view.
        this.depthTexId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, this.depthTexId);
        org.lwjgl.opengl.GL42C.glTexStorage2D(GL_TEXTURE_2D, 1, GL_DEPTH24_STENCIL8, totalW, totalH);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);

        glBindTexture(GL_TEXTURE_2D, 0);

        // FBO with COLOR0 + COLOR1 + DEPTH_STENCIL attachments.
        this.framebufferId = glGenFramebuffers();
        int prevFb = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        glBindFramebuffer(GL_FRAMEBUFFER, this.framebufferId);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, this.colourTexId, 0);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, this.metaTexId, 0);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_TEXTURE_2D, this.depthTexId, 0);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.IntBuffer drawBuffers = stack.ints(GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1);
            glDrawBuffers(drawBuffers);
        }
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("ModelBakery FBO not complete: 0x" + Integer.toHexString(status));
        }
        glBindFramebuffer(GL_FRAMEBUFFER, prevFb);

        // Allocate persistent scratch buffers once. RGBA8/R32UI/D32F all need
        // 4 bytes per pixel, so a single sizing rule fits all three.
        long pixels = (long) totalW * totalH;
        this.scratchSize = pixels * 4L;
        this.colourScratch = MemoryUtil.nmemAllocChecked(this.scratchSize);
        this.depthScratch  = MemoryUtil.nmemAllocChecked(this.scratchSize);
        this.metaScratch   = MemoryUtil.nmemAllocChecked(this.scratchSize);
    }

    /**
     * Synchronously read the FBO contents and pack them into {@code destAddr}
     * in the legacy bufferreorder.comp output layout: 2× uvec2 per pixel —
     * <code>(packedRGBA, packedDepthStencilTint)</code>.
     *
     * Called once per bake immediately after the FBO drawing completes
     * (callers already issue a {@code glMemoryBarrier} before calling this).
     * Uses {@code glFinish} to guarantee the FBO writes are visible to
     * {@code glGetTexImage}. Apple's GL 4.1 driver implements both calls
     * natively.
     */
    public void emitToStream(long destAddr) {
        // Make sure the FBO's draw calls have produced pixel data before we
        // sample the textures back to CPU.
        glFinish();

        int totalW = this.width * 3;
        int totalH = this.height * 2;
        int totalPixels = totalW * totalH;

        // Apple's GL pixel processor (libGLImage's glgProcessPixelsWithProcessor
        // / glgVectorCopy) is fragile on Apple Silicon: it crashes with
        // SIGBUS BUS_ADRALN both via glGetTexImage (hs_err_pid73201/73394)
        // and via glReadPixels (hs_err_pid73671) on the *second* bake
        // invocation. We mitigate three ways: (1) persistent per-instance
        // scratch buffers (constructor) instead of malloc/free per call, so
        // the driver can never race a deferred pack against freed memory;
        // (2) glFinish between each glReadPixels so the pack pipeline has
        // fully drained before we change READ_BUFFER or format; (3) PACK
        // alignment forced to 1 so row padding can't mislead the vector
        // copy's stride math.
        int prevReadFb = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int prevReadBuf = glGetInteger(GL_READ_BUFFER);
        int prevPackAlign = glGetInteger(org.lwjgl.opengl.GL11C.GL_PACK_ALIGNMENT);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, this.framebufferId);
        org.lwjgl.opengl.GL11C.glPixelStorei(org.lwjgl.opengl.GL11C.GL_PACK_ALIGNMENT, 1);
        try {
            glReadBuffer(GL_COLOR_ATTACHMENT0);
            org.lwjgl.opengl.GL11C.nglReadPixels(0, 0, totalW, totalH, GL_RGBA, GL_UNSIGNED_BYTE, this.colourScratch);
            glFinish();
            org.lwjgl.opengl.GL11C.nglReadPixels(0, 0, totalW, totalH, GL_DEPTH_COMPONENT, GL_FLOAT, this.depthScratch);
            glFinish();
            glReadBuffer(GL_COLOR_ATTACHMENT1);
            org.lwjgl.opengl.GL11C.nglReadPixels(0, 0, totalW, totalH, GL_RED_INTEGER, GL_UNSIGNED_INT, this.metaScratch);
            glFinish();
        } finally {
            org.lwjgl.opengl.GL11C.glPixelStorei(org.lwjgl.opengl.GL11C.GL_PACK_ALIGNMENT, prevPackAlign);
            glReadBuffer(prevReadBuf == 0 ? GL_COLOR_ATTACHMENT0 : prevReadBuf);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, prevReadFb);
        }

        // Sample non-zero pixel detection — count this bake invocation as
        // producing real output if ANY pixel has non-zero colour or depth.
        DIAG_BAKE_INVOCATIONS.incrementAndGet();
        boolean sawNonZero = false;
        for (int i = 0; i < totalPixels && !sawNonZero; i++) {
            int rgba = MemoryUtil.memGetInt(this.colourScratch + i * 4L);
            int ds   = MemoryUtil.memGetInt(this.depthScratch  + i * 4L);
            if (rgba != 0 || ds != 0) sawNonZero = true;
        }
        if (sawNonZero) DIAG_BAKE_NONZERO_PIXEL_INVOCATIONS.incrementAndGet();

        // Pack into the legacy uvec2-per-pixel format the consumer expects:
        //   outPoint.x = RGBA packed
        //   outPoint.y = (depth*0xFFFFFF)<<8 | tint_bit
        long colA = this.colourScratch;
        long depA = this.depthScratch;
        long metA = this.metaScratch;
        long outA = destAddr;
        final int DEPTH_MASK = 0xFFFFFF;
        for (int i = 0; i < totalPixels; i++) {
            int rgba = MemoryUtil.memGetInt(colA);   colA += 4;
            float depthF = MemoryUtil.memGetFloat(depA);   depA += 4;
            int meta = MemoryUtil.memGetInt(metA);   metA += 4;
            int depthBits = (int)(depthF * DEPTH_MASK) & DEPTH_MASK;
            int value = (depthBits << 8) | ((meta & 1) << 7);
            MemoryUtil.memPutInt(outA,     rgba);
            MemoryUtil.memPutInt(outA + 4, value);
            outA += 8;
        }
    }

    /**
     * Clear the FBO between bakes. GL 3.0 bind+glClearBuffer path so it works
     * on Apple's GL 4.1 cap (the previous {@code glClearNamedFramebufferfi}
     * route was GL 4.5 DSA).
     */
    public void clear() {
        int prevFb = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        glBindFramebuffer(GL_FRAMEBUFFER, this.framebufferId);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.FloatBuffer zerof = stack.floats(0, 0, 0, 0);
            java.nio.IntBuffer zeroi = stack.ints(0, 0, 0, 0);
            glClearBufferfv(GL_COLOR, 0, zerof);
            glClearBufferuiv(GL_COLOR, 1, zeroi);
        }
        glClearBufferfi(GL_DEPTH_STENCIL, 0, 1.0f, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, prevFb);
    }

    public void free() {
        glDeleteFramebuffers(this.framebufferId);
        glDeleteTextures(this.colourTexId);
        glDeleteTextures(this.depthTexId);
        glDeleteTextures(this.metaTexId);
        if (this.colourScratch != 0L) MemoryUtil.nmemFree(this.colourScratch);
        if (this.depthScratch  != 0L) MemoryUtil.nmemFree(this.depthScratch);
        if (this.metaScratch   != 0L) MemoryUtil.nmemFree(this.metaScratch);
    }
}
