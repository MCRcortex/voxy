package me.cortex.voxy.client.core.gpu;

import me.cortex.voxy.common.Logger;

/**
 * Factory for creating the appropriate RenderBackend based on the current platform.
 *
 * On macOS with Apple Silicon (M-series), the Metal backend will be preferred.
 * On all other platforms, the OpenGL backend is used.
 */
public final class RenderBackendFactory {
    private static RenderBackend INSTANCE;

    private RenderBackendFactory() {}

    /**
     * Gets or creates the singleton RenderBackend instance.
     * The backend type is automatically selected based on platform capabilities.
     */
    public static RenderBackend get() {
        if (INSTANCE == null) {
            INSTANCE = createBackend();
        }
        return INSTANCE;
    }

    /**
     * Force-sets the backend (useful for testing or explicit user override).
     */
    public static void set(RenderBackend backend) {
        INSTANCE = backend;
    }

    private static RenderBackend createBackend() {
        if (shouldUseMetal()) {
            Logger.info("Metal backend requested but not yet implemented, falling back to OpenGL");
            // TODO: return new MetalRenderBackend(); when Metal implementation is ready
        }
        Logger.info("Using OpenGL render backend");
        // Lazy import to avoid class loading issues on platforms without OpenGL
        return new me.cortex.voxy.client.core.gl.GlRenderBackend();
    }

    private static boolean shouldUseMetal() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        // Apple Silicon Macs: macOS + aarch64
        return os.contains("mac") && arch.contains("aarch64");
    }
}
