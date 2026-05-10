package me.cortex.voxy.client.core.gpu.shader;

import me.cortex.voxy.common.Logger;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

import static org.lwjgl.util.shaderc.Shaderc.*;
import static org.lwjgl.util.spvc.Spvc.*;

/**
 * Compiles Voxy's GLSL shaders to SPIRV and MSL at runtime via LWJGL's
 * shaderc and SPIRV-Cross bindings.
 *
 * Used by the Metal and Vulkan render backends; the OpenGL backend keeps
 * driver-side compilation and never invokes this class. Natives are bundled
 * only for macos-arm64.
 *
 * Voxy's existing Shader.Builder injects #defines per pipeline permutation —
 * those defines are forwarded here, so the same .comp/.vert/.frag source
 * naturally yields different SPIRV/MSL outputs per call site without a
 * build-time permutation manifest.
 *
 * Results are cached on disk under ~/.voxy/shader-cache/ keyed by sha256 of
 * (expanded GLSL source, stage, sorted defines, target). Cache survives JVM
 * restarts so a flythrough doesn't pay compile latency twice.
 */
public final class RuntimeShaderCompiler {

    public enum Stage {
        VERTEX(shaderc_glsl_vertex_shader),
        FRAGMENT(shaderc_glsl_fragment_shader),
        COMPUTE(shaderc_glsl_compute_shader),
        GEOMETRY(shaderc_glsl_geometry_shader),
        TESS_CONTROL(shaderc_glsl_tess_control_shader),
        TESS_EVAL(shaderc_glsl_tess_evaluation_shader);
        public final int shadercKind;
        Stage(int kind) { this.shadercKind = kind; }
    }

    public enum Target { METAL_MSL, VULKAN_SPIRV }

    public record Result(byte[] spirv, String mslSource) {}

    private static final Path CACHE_DIR;
    static {
        String home = System.getProperty("user.home", System.getProperty("java.io.tmpdir"));
        CACHE_DIR = Path.of(home, ".voxy", "shader-cache");
        try { Files.createDirectories(CACHE_DIR); } catch (Exception ignored) {}
    }

    private RuntimeShaderCompiler() {}

    public static Result compile(String glslSource, Stage stage, Map<String, String> defines, Target target) {
        String key = cacheKey(glslSource, stage, defines, target);
        Path spvFile = CACHE_DIR.resolve(key + ".spv");
        Path mslFile = CACHE_DIR.resolve(key + ".metal");

        if (Files.exists(spvFile)) {
            try {
                byte[] spv = Files.readAllBytes(spvFile);
                String msl = (target == Target.METAL_MSL && Files.exists(mslFile))
                        ? Files.readString(mslFile, StandardCharsets.UTF_8) : null;
                if (target != Target.METAL_MSL || msl != null) {
                    return new Result(spv, msl);
                }
            } catch (Exception e) {
                Logger.warn("Voxy shader cache read failed, recompiling: " + e.getMessage());
            }
        }

        byte[] spv = compileToSpirv(glslSource, stage, defines);
        String msl = (target == Target.METAL_MSL) ? transpileSpirvToMsl(spv) : null;

        try {
            Files.write(spvFile, spv);
            if (msl != null) Files.writeString(mslFile, msl, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Logger.warn("Voxy shader cache write failed: " + e.getMessage());
        }

        return new Result(spv, msl);
    }

    private static byte[] compileToSpirv(String src, Stage stage, Map<String, String> defines) {
        long compiler = shaderc_compiler_initialize();
        if (compiler == 0L) throw new RuntimeException("shaderc_compiler_initialize returned 0");
        long options = shaderc_compile_options_initialize();
        if (options == 0L) {
            shaderc_compiler_release(compiler);
            throw new RuntimeException("shaderc_compile_options_initialize returned 0");
        }
        try {
            // Voxy shaders are GLSL written for OpenGL semantics. Targeting Vulkan 1.2 + SPIRV 1.3
            // gives us subgroup ops (used by hiz.comp) and works with both MoltenVK and SPIRV-Cross MSL.
            // auto_bind_uniforms and auto_map_locations let Voxy's traditional `layout(binding=N)`
            // pattern work without forcing every shader to add explicit `set=0`/`location=N` decorations.
            shaderc_compile_options_set_target_env(options, shaderc_target_env_vulkan, shaderc_env_version_vulkan_1_2);
            shaderc_compile_options_set_target_spirv(options, shaderc_spirv_version_1_3);
            shaderc_compile_options_set_optimization_level(options, shaderc_optimization_level_performance);
            shaderc_compile_options_set_auto_bind_uniforms(options, true);
            shaderc_compile_options_set_auto_map_locations(options, true);

            // Voxy's shaders are written for OpenGL semantics; remap GL built-ins to their
            // Vulkan equivalents so a single source compiles for both backends. Same source
            // continues to compile under the GL backend (which uses the driver, not this
            // class), so these defines never reach the OpenGL path.
            shaderc_compile_options_add_macro_definition(options, "VOXY_VULKAN", "1");
            shaderc_compile_options_add_macro_definition(options, "gl_VertexID", "gl_VertexIndex");
            shaderc_compile_options_add_macro_definition(options, "gl_InstanceID", "gl_InstanceIndex");

            for (var e : defines.entrySet()) {
                shaderc_compile_options_add_macro_definition(options, e.getKey(), e.getValue());
            }

            long result = shaderc_compile_into_spv(compiler, src, stage.shadercKind, "voxy_shader", "main", options);
            if (result == 0L) throw new RuntimeException("shaderc_compile_into_spv returned 0");
            try {
                int status = shaderc_result_get_compilation_status(result);
                if (status != shaderc_compilation_status_success) {
                    String err = shaderc_result_get_error_message(result);
                    long nErr = shaderc_result_get_num_errors(result);
                    long nWarn = shaderc_result_get_num_warnings(result);
                    throw new RuntimeException("shaderc compile failed (status=" + status
                            + ", " + nErr + " errors, " + nWarn + " warnings):\n" + err);
                }
                ByteBuffer bytes = shaderc_result_get_bytes(result);
                byte[] spv = new byte[bytes.remaining()];
                bytes.get(spv);
                return spv;
            } finally {
                shaderc_result_release(result);
            }
        } finally {
            shaderc_compile_options_release(options);
            shaderc_compiler_release(compiler);
        }
    }

    private static String transpileSpirvToMsl(byte[] spv) {
        if ((spv.length & 3) != 0) throw new RuntimeException("SPIRV blob length is not a multiple of 4 bytes: " + spv.length);
        ByteBuffer spvBuf = MemoryUtil.memAlloc(spv.length);
        spvBuf.put(spv).flip();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer ctxPtr = stack.callocPointer(1);
            int s = spvc_context_create(ctxPtr);
            if (s != SPVC_SUCCESS) throw new RuntimeException("spvc_context_create failed: " + s);
            long ctx = ctxPtr.get(0);
            try {
                IntBuffer words = spvBuf.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
                PointerBuffer irPtr = stack.callocPointer(1);
                s = spvc_context_parse_spirv(ctx, words, words.remaining(), irPtr);
                if (s != SPVC_SUCCESS) {
                    throw new RuntimeException("spvc_context_parse_spirv failed: " + s
                            + " — " + spvc_context_get_last_error_string(ctx));
                }
                long parsedIr = irPtr.get(0);

                PointerBuffer compPtr = stack.callocPointer(1);
                s = spvc_context_create_compiler(ctx, SPVC_BACKEND_MSL, parsedIr,
                        SPVC_CAPTURE_MODE_TAKE_OWNERSHIP, compPtr);
                if (s != SPVC_SUCCESS) {
                    throw new RuntimeException("spvc_context_create_compiler(MSL) failed: " + s
                            + " — " + spvc_context_get_last_error_string(ctx));
                }
                long compilerHandle = compPtr.get(0);

                PointerBuffer optsPtr = stack.callocPointer(1);
                s = spvc_compiler_create_compiler_options(compilerHandle, optsPtr);
                if (s != SPVC_SUCCESS) throw new RuntimeException("spvc_compiler_create_compiler_options failed: " + s);
                long opts = optsPtr.get(0);

                // MSL 3.0 (Apple Silicon supports up to MSL 3.x). 30000 = 3.0.0 in spvc encoding.
                spvc_compiler_options_set_uint(opts, SPVC_COMPILER_OPTION_MSL_VERSION, 30000);
                // Direct buffer/texture bindings (one [[buffer(N)]] per resource) instead of
                // argument buffers. Argument buffers would require building a separate
                // descriptor MTLBuffer per draw, which doesn't match Voxy's per-shader-binding
                // pattern. Direct bindings let setBuffer(N, ...) hit the right MSL slot.
                spvc_compiler_options_set_bool(opts, SPVC_COMPILER_OPTION_MSL_ARGUMENT_BUFFERS, false);
                // Voxy shaders use explicit binding=N decorations everywhere; honor them.
                spvc_compiler_options_set_bool(opts, SPVC_COMPILER_OPTION_MSL_ENABLE_DECORATION_BINDING, true);

                s = spvc_compiler_install_compiler_options(compilerHandle, opts);
                if (s != SPVC_SUCCESS) throw new RuntimeException("spvc_compiler_install_compiler_options failed: " + s);

                PointerBuffer outPtr = stack.callocPointer(1);
                s = spvc_compiler_compile(compilerHandle, outPtr);
                if (s != SPVC_SUCCESS) {
                    throw new RuntimeException("spvc_compiler_compile failed: " + s
                            + " — " + spvc_context_get_last_error_string(ctx));
                }
                long strPtr = outPtr.get(0);
                return MemoryUtil.memUTF8(strPtr);
            } finally {
                spvc_context_destroy(ctx);
            }
        } finally {
            MemoryUtil.memFree(spvBuf);
        }
    }

    private static String cacheKey(String src, Stage stage, Map<String, String> defines, Target target) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(src.getBytes(StandardCharsets.UTF_8));
            md.update((byte) stage.ordinal());
            md.update((byte) target.ordinal());
            for (var e : new TreeMap<>(defines).entrySet()) {
                md.update(e.getKey().getBytes(StandardCharsets.UTF_8));
                md.update((byte) '=');
                md.update(e.getValue().getBytes(StandardCharsets.UTF_8));
                md.update((byte) '\n');
            }
            byte[] hash = md.digest();
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
