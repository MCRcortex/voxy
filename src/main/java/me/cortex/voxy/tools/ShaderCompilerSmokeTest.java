package me.cortex.voxy.tools;

import me.cortex.voxy.client.core.gpu.shader.RuntimeShaderCompiler;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Standalone smoke test for {@link RuntimeShaderCompiler}: walks Voxy's
 * shaders/ tree, attempts GLSL → SPIRV → MSL for each stage with the
 * runtime defines we pulled from grep'ing the Java source, and reports
 * pass/fail counts plus first-failure error per shader.
 *
 * Invoked via the {@code testShaderCompiler} Gradle task — not part of the
 * mod runtime. Lives under {@code me.cortex.voxy.tools} to make that obvious.
 */
public final class ShaderCompilerSmokeTest {

    private ShaderCompilerSmokeTest() {}

    private record ShaderCase(String relPath, RuntimeShaderCompiler.Stage stage, Map<String, String> defines, String label) {}

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length > 0 ? args[0] : "src/main/resources/assets/voxy/shaders").toAbsolutePath();
        if (!Files.isDirectory(root)) {
            System.err.println("Shaders directory not found: " + root);
            System.exit(2);
        }

        Map<String, String> empty = Map.of();
        // Defines pulled from MDICSectionRenderer.java:58–91 — cmdgen.comp gets compiled twice
        // with different TRANSLUCENT_DISTANCE_BUFFER_BINDING values per pipeline.
        Map<String, String> cmdgenA = Map.of(
                "TRANSLUCENT_WRITE_BASE", "1024",
                "TEMPORAL_OFFSET", "0",
                "TRANSLUCENT_DISTANCE_BUFFER_BINDING", "7",
                "HAS_STATISTICS", "1",
                "STATISTICS_BUFFER_BINDING", "8");

        // Defines for M9-migrated shaders — keep in sync with the Java callers
        // (FullscreenBlit constructors in AbstractRenderPipeline / NormalRenderPipeline).
        Map<String, String> blitDepthCutoutFog = Map.of("EMIT_COLOUR", "", "USE_ENV_FOG", "");

        ShaderCase[] cases = new ShaderCase[]{
                new ShaderCase("post/noop.frag", RuntimeShaderCompiler.Stage.FRAGMENT, empty, "post/noop.frag (no defines)"),
                new ShaderCase("post/blit_texture_cutout.frag", RuntimeShaderCompiler.Stage.FRAGMENT, empty, "post/blit_texture_cutout.frag"),
                new ShaderCase("post/depth0.frag", RuntimeShaderCompiler.Stage.FRAGMENT, empty, "post/depth0.frag"),
                new ShaderCase("post/depth_copy.frag", RuntimeShaderCompiler.Stage.FRAGMENT, empty, "post/depth_copy.frag (M9 — UBO push)"),
                new ShaderCase("post/blit_texture_depth_cutout.frag", RuntimeShaderCompiler.Stage.FRAGMENT, empty, "post/blit_texture_depth_cutout.frag (Iris path — no EMIT_COLOUR)"),
                new ShaderCase("post/blit_texture_depth_cutout.frag", RuntimeShaderCompiler.Stage.FRAGMENT, blitDepthCutoutFog, "post/blit_texture_depth_cutout.frag (NormalRenderPipeline — EMIT_COLOUR + USE_ENV_FOG)"),
                new ShaderCase("hiz/blit.fsh", RuntimeShaderCompiler.Stage.FRAGMENT, empty, "hiz/blit.fsh"),
                new ShaderCase("post/fullscreen.vert", RuntimeShaderCompiler.Stage.VERTEX, empty, "post/fullscreen.vert"),
                new ShaderCase("hiz/blit.vsh", RuntimeShaderCompiler.Stage.VERTEX, empty, "hiz/blit.vsh (M9 — TRIANGLE_STRIP corners)"),
                new ShaderCase("chunkoutline/outline.vsh", RuntimeShaderCompiler.Stage.VERTEX, empty, "chunkoutline/outline.vsh (M9 — integer-mix extension)"),
                new ShaderCase("chunkoutline/outline.fsh", RuntimeShaderCompiler.Stage.FRAGMENT, empty, "chunkoutline/outline.fsh"),
                new ShaderCase("lod/gl46/prep.comp", RuntimeShaderCompiler.Stage.COMPUTE, empty, "lod/gl46/prep.comp"),
                new ShaderCase("hiz/hiz.comp", RuntimeShaderCompiler.Stage.COMPUTE, empty, "hiz/hiz.comp (subgroups)"),
                new ShaderCase("lod/gl46/cmdgen.comp", RuntimeShaderCompiler.Stage.COMPUTE, cmdgenA, "lod/gl46/cmdgen.comp + injected defines"),
                new ShaderCase("lod/hierarchical/debug/setup.comp", RuntimeShaderCompiler.Stage.COMPUTE, empty, "lod/hierarchical/debug/setup.comp"),
                new ShaderCase("util/scatter.comp", RuntimeShaderCompiler.Stage.COMPUTE,
                        Map.of("INPUT_BUFFER_BINDING", "0", "OUTPUT_BUFFER1_BINDING", "1", "OUTPUT_BUFFER2_BINDING", "2", "PUSH_BINDING", "14"),
                        "util/scatter.comp (M9 — UBO push)"),
                new ShaderCase("util/memcpy.comp", RuntimeShaderCompiler.Stage.COMPUTE,
                        Map.of("INPUT_HEADER_BUFFER_BINDING", "0", "INPUT_DATA_BUFFER_BINDING", "1", "OUTPUT_BUFFER_BINDING", "2"),
                        "util/memcpy.comp"),
                new ShaderCase("bakery/position_tex.vsh", RuntimeShaderCompiler.Stage.VERTEX,
                        Map.of("PUSH_BINDING", "14"),
                        "bakery/position_tex.vsh (M9 — UBO push)"),
                new ShaderCase("bakery/position_tex.fsh", RuntimeShaderCompiler.Stage.FRAGMENT, empty,
                        "bakery/position_tex.fsh (M9 — binding-based sampler)"),
                // MDIC's terrain shaders — realistic defines for the non-Iris,
                // non-debug, no-NV-barrycoords path (the configuration Voxy on
                // Mac will run with first).
                new ShaderCase("lod/gl46/quads3.vert", RuntimeShaderCompiler.Stage.VERTEX,
                        Map.of(
                                "NO_SHADE_FACE_TINT", "1.0",
                                "UP_FACE_TINT", "1.0",
                                "DOWN_FACE_TINT", "0.5",
                                "Z_AXIS_FACE_TINT", "0.8",
                                "X_AXIS_FACE_TINT", "0.6"),
                        "lod/gl46/quads3.vert (MDIC terrain — non-Iris baseline)"),
                new ShaderCase("lod/gl46/quads.frag", RuntimeShaderCompiler.Stage.FRAGMENT, empty,
                        "lod/gl46/quads.frag (MDIC terrain — non-Iris baseline; PATCHED_SHADER undef)"),
                // M9 — MDIC's compute pipelines (cmdgen already covered above).
                new ShaderCase("util/prefixsum/simple.comp", RuntimeShaderCompiler.Stage.COMPUTE,
                        Map.of("IO_BUFFER", "0"),
                        "util/prefixsum/simple.comp (MDIC prefix-sum fallback)"),
                new ShaderCase("util/prefixsum/inital3.comp", RuntimeShaderCompiler.Stage.COMPUTE,
                        Map.of("IO_BUFFER", "0"),
                        "util/prefixsum/inital3.comp (MDIC prefix-sum subgroup)"),
                new ShaderCase("lod/gl46/buildtranslucents.comp", RuntimeShaderCompiler.Stage.COMPUTE,
                        Map.of(
                                "TRANSLUCENT_WRITE_BASE", "1024",
                                "TRANSLUCENT_DISTANCE_BUFFER_BINDING", "5",
                                "TRANSLUCENT_OFFSET", "500000"),
                        "lod/gl46/buildtranslucents.comp"),
                new ShaderCase("lod/gl46/cull/raster.vert", RuntimeShaderCompiler.Stage.VERTEX, empty,
                        "lod/gl46/cull/raster.vert"),
                new ShaderCase("lod/gl46/cull/raster.frag", RuntimeShaderCompiler.Stage.FRAGMENT, empty,
                        "lod/gl46/cull/raster.frag"),
                // M12 chunk 5 Metal cull stub — force-all-visible compute.
                new ShaderCase("lod/gl46/force_all_visible.comp", RuntimeShaderCompiler.Stage.COMPUTE, empty,
                        "lod/gl46/force_all_visible.comp (M12 Metal cull stub)"),
        };

        int passSpv = 0, failSpv = 0, passMsl = 0, failMsl = 0;
        StringBuilder failures = new StringBuilder();
        long start = System.nanoTime();
        // assetsBase is the directory CONTAINING `assets/`, i.e. the resource root
        // (src/main/resources). expand() prepends "assets/" + namespace + "/shaders/".
        Path assetsBase = root.getParent().getParent().getParent();
        for (ShaderCase c : cases) {
            String src = expandImports(root.resolve(c.relPath), assetsBase);
            RuntimeShaderCompiler.Result spvResult;
            try {
                spvResult = RuntimeShaderCompiler.compile(src, c.stage, c.defines, RuntimeShaderCompiler.Target.VULKAN_SPIRV);
                System.out.printf("PASS spv  %5d B  %s%n", spvResult.spirv().length, c.label);
                passSpv++;
            } catch (Throwable t) {
                System.out.printf("FAIL spv             %s%n", c.label);
                failures.append("  ").append(c.label).append(" [SPIRV]: ").append(t.getMessage()).append('\n');
                failSpv++;
                continue;
            }
            try {
                RuntimeShaderCompiler.Result mslResult = RuntimeShaderCompiler.compile(src, c.stage, c.defines, RuntimeShaderCompiler.Target.METAL_MSL);
                int mslLen = mslResult.mslSource() == null ? 0 : mslResult.mslSource().length();
                System.out.printf("PASS msl  %5d ch %s%n", mslLen, c.label);
                passMsl++;
            } catch (Throwable t) {
                System.out.printf("FAIL msl             %s%n", c.label);
                failures.append("  ").append(c.label).append(" [MSL]: ").append(t.getMessage()).append('\n');
                failMsl++;
            }
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.println();
        System.out.printf("=== SPV: %d/%d  MSL: %d/%d  in %d ms ===%n",
                passSpv, passSpv + failSpv, passMsl, passMsl + failMsl, ms);
        if (failures.length() > 0) {
            System.out.println("Failures:");
            System.out.println(failures);
            System.exit(1);
        }
    }

    /** Resolve Voxy's #import &lt;ns:path&gt; directives recursively against the assets root. */
    private static String expandImports(Path file, Path assetsRoot) throws Exception {
        return expand(file, assetsRoot, new java.util.HashSet<>());
    }

    private static String expand(Path file, Path assetsRoot, java.util.Set<String> seen) throws Exception {
        String key = file.toAbsolutePath().toString();
        if (!seen.add(key)) return ""; // already included; first wins
        StringBuilder out = new StringBuilder();
        for (String line : Files.readString(file, StandardCharsets.UTF_8).split("\n", -1)) {
            String trimmed = line.strip();
            if (trimmed.startsWith("#import")) {
                int lt = trimmed.indexOf('<');
                int gt = trimmed.indexOf('>');
                if (lt >= 0 && gt > lt) {
                    String ref = trimmed.substring(lt + 1, gt);
                    int colon = ref.indexOf(':');
                    if (colon < 0) { out.append(line).append('\n'); continue; }
                    String ns = ref.substring(0, colon);
                    String rel = ref.substring(colon + 1);
                    Path target = assetsRoot.resolve("assets/" + ns + "/shaders/" + rel);
                    out.append("// >>> ").append(ref).append('\n');
                    out.append(expand(target, assetsRoot, seen));
                    out.append("// <<< ").append(ref).append('\n');
                    continue;
                }
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }
}
