package me.cortex.voxy.client.core.vulkan;

import me.cortex.voxy.common.Logger;
import org.lwjgl.system.Configuration;
import org.lwjgl.vulkan.VK;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Bootstraps LWJGL's Vulkan bindings against MoltenVK on macOS Apple Silicon.
 *
 * Standard ICD discovery isn't available on macOS the way it is on Linux/Windows
 * (no driver lives in /etc/vulkan), so we point LWJGL directly at MoltenVK
 * before the first Vulkan call. The dylib is shipped at
 * {@code /natives/macos-arm64/libMoltenVK.dylib} inside the mod jar; on dev
 * machines it can also be picked up from {@code /opt/homebrew/lib} or
 * {@code java.library.path} when present.
 *
 * Loading order:
 *   1. {@code java.library.path} system property (dev convenience).
 *   2. Standard Homebrew install location (/opt/homebrew/lib/libMoltenVK.dylib).
 *   3. The dylib bundled inside the mod jar, extracted to a temp file.
 *
 * Once the path is resolved we set
 * {@code Configuration.VULKAN_LIBRARY_NAME} and call {@link VK#create()} once
 * — subsequent calls are no-ops.
 */
public final class VulkanLoader {

    private static boolean attempted = false;
    private static boolean available = false;

    private VulkanLoader() {}

    public static synchronized boolean load() {
        if (attempted) return available;
        attempted = true;

        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (!(os.contains("mac") && arch.contains("aarch64"))) {
            Logger.info("Vulkan/MoltenVK skipped: requires macOS aarch64 (got " + os + "/" + arch + ")");
            available = false;
            return false;
        }

        String resolved = resolveMoltenVKPath();
        if (resolved == null) {
            Logger.warn("MoltenVK dylib not found in any expected location");
            available = false;
            return false;
        }

        try {
            Configuration.VULKAN_LIBRARY_NAME.set(resolved);
            try {
                VK.create();
                Logger.info("Vulkan/MoltenVK loaded from " + resolved);
            } catch (IllegalStateException already) {
                // LWJGL auto-initializes Vulkan on first use of any vk class; if a
                // previous load picked up the right library (e.g. from java.library.path
                // or DYLD_LIBRARY_PATH) we keep using that one. Configuration changes
                // can't replace an already-loaded library at runtime, so this is the
                // best we can do without a JVM restart.
                Logger.info("Vulkan was already initialized — using existing loader (Configuration.VULKAN_LIBRARY_NAME ignored at this point)");
            }
            available = true;
            return true;
        } catch (Throwable t) {
            Logger.error("Vulkan/MoltenVK initialization failed: " + t.getMessage());
            available = false;
            return false;
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    private static String resolveMoltenVKPath() {
        // 1) java.library.path takes precedence so devs can override during testing.
        String libraryPath = System.getProperty("java.library.path", "");
        for (String entry : libraryPath.split(java.io.File.pathSeparator)) {
            if (entry.isEmpty()) continue;
            Path candidate = Path.of(entry, "libMoltenVK.dylib");
            if (Files.isReadable(candidate)) return candidate.toAbsolutePath().toString();
        }

        // 2) Standard Homebrew Apple Silicon prefix — common on dev machines.
        Path brew = Path.of("/opt/homebrew/lib/libMoltenVK.dylib");
        if (Files.isReadable(brew)) return brew.toString();

        // 3) Bundled in the mod jar — extract to a temp file and load from there.
        return extractFromJar();
    }

    private static String extractFromJar() {
        try (InputStream in = VulkanLoader.class.getResourceAsStream("/natives/macos-arm64/libMoltenVK.dylib")) {
            if (in == null) return null;
            Path tmp = Files.createTempFile("libMoltenVK", ".dylib");
            tmp.toFile().deleteOnExit();
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            return tmp.toAbsolutePath().toString();
        } catch (IOException e) {
            Logger.warn("Failed to extract bundled libMoltenVK.dylib: " + e.getMessage());
            return null;
        }
    }
}
