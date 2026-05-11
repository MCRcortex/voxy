package me.cortex.voxy.client.core.interop;

import me.cortex.voxy.client.core.gpu.BackendType;
import me.cortex.voxy.client.core.gpu.RenderBackendFactory;
import me.cortex.voxy.client.core.metal.MetalNative;
import me.cortex.voxy.client.core.metal.MetalRenderBackend;
import me.cortex.voxy.common.Logger;
import org.lwjgl.system.MemoryUtil;

/**
 * Visible end-to-end demo of the IOSurface bridge inside Minecraft.
 *
 * Toggled by {@code -Dvoxy.bridgeDemo=true}. When enabled and running on
 * the Metal backend, allocates an IOSurface-backed shared texture, paints
 * an animated color into it every frame from Metal, and exposes a GL
 * texture name that MC's render thread can sample. A mixin in MC's
 * LevelRenderer call site (see {@code MixinLevelRendererBridgeDemo}) calls
 * {@link #tickAndPaint} once per frame and reads {@link #glTextureName}
 * for the compositing draw.
 *
 * Purpose: confirm the JNI plumbing for IOSurface + CGLTexImageIOSurface2D
 * works inside an actual MC context. Does NOT render Voxy's LOD chunks —
 * that needs M11's full integration (MDIC → ICB migration, MC framebuffer
 * mixin, sync). This demo just proves the bridge round-trip is wired.
 */
public final class IOSurfaceBridgeDemo {

    /** System property toggle. */
    public static final String ENABLE_PROP = "voxy.bridgeDemo";
    /** Environment variable toggle (preferred — `-D` doesn't always reach the child MC JVM via Gradle/Loom). */
    public static final String ENABLE_ENV  = "VOXY_BRIDGE_DEMO";

    public static final boolean ENABLED =
            "true".equals(System.getProperty(ENABLE_PROP, "false"))
                    || "1".equals(System.getenv(ENABLE_ENV))
                    || "true".equals(System.getenv(ENABLE_ENV));

    private static final int DEMO_WIDTH  = 256;
    private static final int DEMO_HEIGHT = 256;

    private static IOSurfaceBridge bridge;
    private static int glTextureName;
    /** Captured MetalRenderBackend; null until lazyInit succeeds. */
    private static MetalRenderBackend backend;
    /** Frame counter for animated clear color. */
    private static int frame;
    /** Set true when init has been attempted (success or failure). */
    private static boolean initAttempted;
    /** Set true when init succeeded and tickAndPaint should run. */
    private static boolean ready;

    private IOSurfaceBridgeDemo() {}

    public static int glTextureName() {
        return glTextureName;
    }

    public static int width()  { return DEMO_WIDTH;  }
    public static int height() { return DEMO_HEIGHT; }

    /** Whether the demo is fully wired and producing frames. */
    public static boolean isReady() {
        return ready;
    }

    /**
     * Per-frame entry point: lazy-init on first call, then paint the next
     * frame into the Metal side of the bridge. Caller (the mixin in MC's
     * LevelRenderer) handles the GL-side compositing draw using
     * {@link #glTextureName}.
     */
    public static void tickAndPaint() {
        if (!ENABLED) return;
        if (!initAttempted) {
            initAttempted = true;
            try {
                lazyInit();
            } catch (Throwable t) {
                Logger.error("IOSurfaceBridgeDemo init failed; disabling demo", t);
                ready = false;
                return;
            }
        }
        if (!ready) return;
        paintFrame();
    }

    private static void lazyInit() {
        if (RenderBackendFactory.get().getType() != BackendType.METAL) {
            Logger.info("IOSurfaceBridgeDemo: not on Metal backend, demo idle");
            return;
        }
        var be = RenderBackendFactory.get();
        if (!(be instanceof MetalRenderBackend mrb)) {
            Logger.warn("IOSurfaceBridgeDemo: backend reports METAL but isn't a MetalRenderBackend");
            return;
        }
        backend = mrb;

        bridge = IOSurfaceBridge.create(backend.device(), DEMO_WIDTH, DEMO_HEIGHT,
                IOSurfaceBridge.IOSurfaceFormat.BGRA8);
        Logger.info("IOSurfaceBridgeDemo: allocated " + DEMO_WIDTH + "x" + DEMO_HEIGHT
                + " IOSurface bridge (handle=0x" + Long.toHexString(bridge.ioSurfaceHandle()) + ")");

        // Create a GL texture and pin the IOSurface to it. Requires the
        // calling thread to have a current CGL context — by the time the
        // mixin first calls us, MC's render thread does.
        long cglCtx = MetalNative.cglGetCurrentContext();
        if (cglCtx == 0) {
            Logger.warn("IOSurfaceBridgeDemo: no current CGL context on init thread; "
                    + "bridge allocated but GL bind deferred. Will retry next frame.");
            return;
        }

        int[] texNames = new int[1];
        org.lwjgl.opengl.GL11C.glGenTextures(texNames);
        glTextureName = texNames[0];
        if (glTextureName == 0) {
            Logger.error("IOSurfaceBridgeDemo: glGenTextures returned 0");
            bridge.close();
            bridge = null;
            return;
        }

        // Required sampler params on GL_TEXTURE_RECTANGLE.
        org.lwjgl.opengl.GL11C.glBindTexture(0x84F5 /* GL_TEXTURE_RECTANGLE */, glTextureName);
        org.lwjgl.opengl.GL11C.glTexParameteri(0x84F5, org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11C.GL_LINEAR);
        org.lwjgl.opengl.GL11C.glTexParameteri(0x84F5, org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11C.GL_LINEAR);
        org.lwjgl.opengl.GL11C.glTexParameteri(0x84F5, org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE);
        org.lwjgl.opengl.GL11C.glTexParameteri(0x84F5, org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE);

        if (!bridge.bindToGlTexture(glTextureName)) {
            Logger.error("IOSurfaceBridgeDemo: bindToGlTexture failed; closing bridge");
            org.lwjgl.opengl.GL11C.glDeleteTextures(glTextureName);
            glTextureName = 0;
            bridge.close();
            bridge = null;
            return;
        }
        org.lwjgl.opengl.GL11C.glBindTexture(0x84F5, 0);

        Logger.info("IOSurfaceBridgeDemo: GL texture " + glTextureName
                + " now backed by IOSurface — ready");
        ready = true;
    }

    private static int demoFbo;

    /**
     * Blit the IOSurface-backed GL texture into the currently bound
     * GL_DRAW_FRAMEBUFFER, scaled to the requested screen rect. Lazy-creates
     * a transient source FBO with the IOSurface texture attached. Caller
     * is responsible for being on the render thread with MC's framebuffer
     * already bound as the draw target.
     */
    public static void blitToBoundFramebuffer(int dstX0, int dstY0, int dstX1, int dstY1) {
        if (!ready || glTextureName == 0) return;
        if (demoFbo == 0) {
            int[] fbos = new int[1];
            org.lwjgl.opengl.GL30C.glGenFramebuffers(fbos);
            demoFbo = fbos[0];
            if (demoFbo == 0) {
                Logger.error("IOSurfaceBridgeDemo: glGenFramebuffers returned 0");
                return;
            }
            int prevReadFB = org.lwjgl.opengl.GL11C.glGetInteger(org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER_BINDING);
            org.lwjgl.opengl.GL30C.glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER, demoFbo);
            org.lwjgl.opengl.GL32C.glFramebufferTexture(
                    org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER,
                    org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0,
                    glTextureName, 0);
            org.lwjgl.opengl.GL30C.glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER, prevReadFB);
        }
        int prevReadFB = org.lwjgl.opengl.GL11C.glGetInteger(org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER_BINDING);
        org.lwjgl.opengl.GL30C.glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER, demoFbo);
        org.lwjgl.opengl.GL30C.glBlitFramebuffer(
                0, 0, DEMO_WIDTH, DEMO_HEIGHT,
                dstX0, dstY0, dstX1, dstY1,
                org.lwjgl.opengl.GL11C.GL_COLOR_BUFFER_BIT,
                org.lwjgl.opengl.GL11C.GL_LINEAR);
        org.lwjgl.opengl.GL30C.glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER, prevReadFB);
    }

    private static void paintFrame() {
        frame++;
        // Animated colour: cycle through hue every ~4 seconds (at 60fps).
        float t = (frame % 240) / 240.0f;
        float r = 0.5f + 0.5f * (float) Math.cos(t * 2 * Math.PI);
        float g = 0.5f + 0.5f * (float) Math.cos((t + 0.33f) * 2 * Math.PI);
        float b = 0.5f + 0.5f * (float) Math.cos((t + 0.66f) * 2 * Math.PI);

        var pass = me.cortex.voxy.client.core.gpu.RenderPassDesc.builder(DEMO_WIDTH, DEMO_HEIGHT)
                .clearColor(new MetalTextureView(bridge.metalTextureHandle()), r, g, b, 1.0f)
                .build();
        try (var enc = backend.beginRenderPass(pass)) {
            // Just clear — no draws. The clear is enough proof Metal wrote
            // to the IOSurface, since GL on the other side will see those
            // pixels.
        }
        backend.submit();
    }

    /**
     * Tiny IGpuTexture adapter that wraps the raw MTLTexture handle from
     * the bridge so we can hand it to the encoder's RenderPassDesc without
     * routing through MetalRenderBackend.createTexture (which would
     * allocate a fresh, non-IOSurface-backed texture).
     */
    private static final class MetalTextureView implements me.cortex.voxy.client.core.gpu.IGpuTexture {
        private final int id;
        MetalTextureView(long mtlHandle) {
            // Register the raw Metal handle with MetalHandleMap so the
            // encoder's bufferHandle(IGpuTexture) lookup resolves it.
            this.id = me.cortex.voxy.client.core.metal.MetalHandleMap.register(mtlHandle);
        }
        @Override public int id() { return this.id; }
        @Override public int getWidth() { return DEMO_WIDTH; }
        @Override public int getHeight() { return DEMO_HEIGHT; }
        @Override public int getLevels() { return 1; }
        @Override public int getFormat() { return 0x8058 /* GL_RGBA8 */; }
        @Override public int getType() { return 0x0DE1 /* GL_TEXTURE_2D */; }
        @Override public me.cortex.voxy.client.core.gpu.IGpuTexture store(int format, int levels, int width, int height) { return this; }
        @Override public me.cortex.voxy.client.core.gpu.IGpuTexture createView() { return this; }
        @Override public me.cortex.voxy.client.core.gpu.IGpuTexture name(String name) { return this; }
        @Override public void assertAllocated() {}
        @Override public void free() { /* handle owned by IOSurfaceBridge */ }
        @Override public void assertNotFreed() {}
        @Override public boolean isFreed() { return false; }
    }
}
