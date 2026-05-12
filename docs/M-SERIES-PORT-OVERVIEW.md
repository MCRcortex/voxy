# Voxy → Mac M-Series Port — Overview

Audience: human developer (or PM) wanting a quick read on where this branch stands.
For the full technical handoff including file paths, gotchas, and APIs, see `M-SERIES-PORT-STATE.md`.

---

## The problem

[Voxy](https://github.com/MCRcortex/voxy) is a Minecraft Java mod that adds far-distance ("LOD") chunk rendering driven by GPU compute shaders. It needs OpenGL 4.3+ features (compute shaders, SSBO, indirect draw with count, persistent mapped buffers, image load/store). Apple deprecated OpenGL on macOS at version 4.1 — none of those features are available — so Voxy currently doesn't run on any Mac, including Apple Silicon (M1+).

This branch is the work-in-progress port to make Voxy run on Mac M-series Macs by adding two new render backends:
- **Metal direct** — uses Apple's native graphics API.
- **Vulkan via MoltenVK** — uses Vulkan 1.2+ (translated to Metal under the hood by MoltenVK).

Both are being prototyped in parallel so we can benchmark them and pick the long-term winner.

---

## The approach

Voxy already had a partial render-backend abstraction (`RenderBackend` interface + `MetalRenderBackend` skeleton) before this branch picked up. We've extended that abstraction so that Voxy's render code can talk to Metal, Vulkan, or OpenGL without per-backend `if` checks at every call site.

The plan, in milestones:

1. **M0–M2:** Get the dev environment working, validate shader translation, expand the abstraction.
2. **M3 / M4:** First render — clear the screen to a color on each backend.
3. **M5 / M6:** Render a triangle on each backend.
4. **M7 / M8:** Run a compute shader on each backend.
5. **M9:** Migrate Voxy's code from raw OpenGL calls to the abstraction (file by file).
6. **M10 / M11:** Bridge Voxy's GPU output back into Minecraft's existing OpenGL pipeline (via Apple `IOSurface`).
7. **M12 / M13:** Validate full LOD distance rendering inside Minecraft.
8. **M14:** Benchmark Metal vs Vulkan and decide.

**M0 through M11 are closed.** We're starting M12 (LOD distance migration) today.

---

## Where we are now

**~30 commits on the branch. 11 smoke tests pass on this Apple M4 Max. Voxy boots, initializes on Metal, and a clear-color stub renders into MC's framebuffer end-to-end inside Minecraft.**

- ✅ Dev env (JDK 24, Xcode CLT, CMake) verified.
- ✅ Runtime shader compiler — translates Voxy's GLSL to SPIRV (for Vulkan) and to MSL (for Metal) on the fly. Disk cache. 24/24 representative shaders compile to SPIRV (8/9 to MSL — `hiz.comp` deferred).
- ✅ Encoder API — Voxy describes render or compute passes through a backend-agnostic API instead of raw GL, with real implementations on Metal **and** GL (so Win/Linux users keep working).
- ✅ **Metal**: clear color, triangle, compute dispatch + SSBO write, vertex layout + indirect draw, indirect command buffers (ICB) all working end-to-end on the GPU.
- ✅ **Vulkan via MoltenVK**: clear color, triangle, compute dispatch with descriptor sets all working.
- ✅ Pipeline state config (depth, blend, cull, polygon mode), samplers, push-constant-equivalent uniforms — all wired up.
- ✅ **M9 file migration**: `NodeCleaner`, `HierarchicalOcclusionTraverser`, `HiZBuffer`, `FullscreenBlit`, `AbstractRenderPipeline`, `NormalRenderPipeline`, `ChunkBoundRenderer`, `AsyncNodeManager`, `BudgetBufferRenderer`, the 5 MDIC non-Iris pipelines, and the 2 MDIC terrain pipelines all create their pipelines through the abstraction. Iris pipelines gated to GL-only by design.
- ✅ **M10 IOSurface bridge**: an IOSurface is wrapped as both an `MTLTexture` (Metal render target) and a `GL_TEXTURE_RECTANGLE` (sampleable from MC's GL compositor), with the GL-side bind path through `CGLTexImageIOSurface2D`.
- ✅ **M11 end-to-end visual verification (run 2026-05-10/11)**: with `VOXY_FORCE_METAL=1`, MC boots, Voxy initializes on Metal, Sodium chunk workers run (macOS arm64 `lwjgl-zstd`/`lwjgl-lmdb` natives bundled), `IOSurfaceBridgeCompositor` blits the bridge into MC's main RT during Sodium's CUTOUT pass, and MC closes cleanly (`AsyncNodeManager` worker is daemon). User confirmed visually: magenta strip in the left 25% + Sodium terrain in the right 75% + clean close.

The native library (`libvoxy_metal.dylib`) exposes ~100 JNI entry points (ICB + IOSurface bridge added in M10/M11).

**Each smoke test has caught at least one real bug.** The `VertexLayout` test caught an off-by-9 in our `MTLVertexFormat` enum mapping that the SDK header would have flagged. The ICB test caught a per-slot pipeline binding requirement.

---

## What's still missing

M12 (LOD distance migration) is the next concrete deliverable. Today the Metal path only renders a clear-color stub into the IOSurface bridge because Voxy's actual rendering code (`MDICSectionRenderer.renderTerrain` / `renderTranslucent` / `renderTemporal` and the 5 compute prepasses in `buildDrawCalls`) still calls raw `glUseProgram` / `glBindBufferBase` / `glMultiDrawElementsIndirectCount*` / `glDispatchCompute*` — on Metal `mdicProgramId(p)` returns 0 so those calls no-op.

To finish M12 we need:

1. **MDIC compute prepasses through `ComputeEncoder`** — the 4 simple compute passes (`prep`, `commandGen`, `prefixSum`, `translucentGen`) need `beginComputePass` + `setBuffer` + `dispatch[Indirect]` + `barrier`. The `cull` pass is a graphics pass (rasterized into a discard-only target) and needs more care.

2. **MDIC render passes through `RenderEncoder`** — `renderTerrain` / `renderTranslucent` / `renderTemporal` need `beginRenderPass` against the IOSurface bridge, plus `setPipeline` + `setBuffer` + `bindIndexBuffer` + `drawIndexedIndirect` (Metal CPU loop) or `drawIndexedIndirectCount` (GL).

3. **`AbstractRenderPipeline.runPipelineMetalStub` replaced** with a real `runPipelineMetal` that opens a render pass against the bridge, invokes the section renderer, then submits.

Plus the remaining shader topology + JSON gaps (per-mip texture views for HiZBuffer2; `hiz.comp` MSL workaround) — both unblock more advanced render passes but aren't on the critical path for M12 acceptance.

Total estimate for M12: **~2–3 days**.

---

## Why we haven't tested in Minecraft yet

Tempting to drop the JAR into Modrinth and hit "play". But until M9 is done, what would happen is:

1. Minecraft loads, Voxy loads, log says `Using Metal render backend (Apple Silicon)`.
2. The first time Voxy tries to render a chunk, it calls raw GL functions (`glDispatchCompute`, `glMultiDrawElementsIndirectCountARB`, etc.) — which don't exist on Mac's GL 4.1 driver.
3. Crash.

The new abstraction is in place but Voxy's rendering code doesn't use it yet. That's M9.

We could do a *partial* test now to confirm Voxy at least loads cleanly and the Metal backend is selected, but it tells us less than people expect. Better to spend the time finishing M9 and then test for real.

---

## What this looks like as a release

Once M9 is done and the IOSurface bridge (M10/M11) is wired up, the Mac user experience should be:

1. Drop the mod JAR into a Modrinth profile (Fabric loader, Sodium 0.8.1, MC 1.21.11).
2. Launch.
3. Voxy auto-selects Metal (or Vulkan, depending on a config flag we'll expose).
4. World renders with full LOD distance, same as Win/Linux.
5. Benchmark numbers compare Metal vs Vulkan to inform whether we keep both backends or consolidate.

That's still ~6 weeks of focused work from where we are today.

---

## Where to look for details

- **Full technical state**: `docs/M-SERIES-PORT-STATE.md` (this folder)
- **Live planning doc**: `~/.claude-personal/plans/context-en-el-prancy-sunrise.md` (outside the repo)
- **Smoke tests**: `src/main/java/me/cortex/voxy/tools/*SmokeTest.java`
- **Encoder API**: `src/main/java/me/cortex/voxy/client/core/gpu/`
- **Metal backend**: `src/main/java/me/cortex/voxy/client/core/metal/`
- **Native bridge**: `native/metal/src/`
- **Branch**: `claude/opengl-mac-migration-analysis-6319V`
