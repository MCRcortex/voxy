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

**M0 through M12 are closed.** We're starting M13 (texture + lighting + depth-import polish) on 2026-05-12.

---

## Where we are now

**~80 commits on the branch. 12 smoke tests pass on this Apple M4 Max. Voxy renders LOD chunks end-to-end on Metal inside Minecraft, behind Sodium's near terrain, full-screen — user-confirmed 2026-05-12.**

- ✅ Dev env (JDK 24, Xcode CLT, CMake) verified.
- ✅ Runtime shader compiler — translates Voxy's GLSL to SPIRV (for Vulkan) and to MSL (for Metal) on the fly. Disk cache. 28 representative shaders compile to SPIRV (27 to MSL — `hiz.comp` deferred).
- ✅ Encoder API — Voxy describes render or compute passes through a backend-agnostic API instead of raw GL, with real implementations on Metal **and** GL (so Win/Linux users keep working).
- ✅ **Metal**: clear color, triangle, compute dispatch + SSBO write, vertex layout + indirect draw, indirect command buffers (ICB), render passes against IOSurface bridges with depth attachments, encoder-based drawIndexedIndirect — all working end-to-end.
- ✅ **Vulkan via MoltenVK**: clear color, triangle, compute dispatch with descriptor sets all working.
- ✅ Pipeline state config (depth, blend, cull, polygon mode), samplers, push-constant-equivalent uniforms — all wired up.
- ✅ **M9 file migration**: every Voxy file that builds a pipeline now goes through the cross-backend abstraction. `AsyncNodeManager`, `NodeCleaner`, `HierarchicalOcclusionTraverser`, `MDICSectionRenderer` (5 compute prepasses + 2 terrain graphics pipelines) are all fully encoder-clean — no raw GL on the hot path. Iris pipelines gated to GL-only by design.
- ✅ **M10 IOSurface bridge**: an IOSurface is wrapped as both an `MTLTexture` (Metal render target) and a `GL_TEXTURE_RECTANGLE` (sampleable from MC's GL compositor), with the GL-side bind path through `CGLTexImageIOSurface2D`.
- ✅ **M11 end-to-end visual verification (run 2026-05-10/11)**: with `VOXY_FORCE_METAL=1`, MC boots, Voxy initializes on Metal, Sodium chunk workers run, the IOSurface bridge composites cleanly. MC closes without hanging.
- ✅ **M12 LOD distance migration (closed 2026-05-12)**: `runPipelineMetal` runs the full pipeline (DownloadStream + AsyncNodeManager + NodeCleaner + HOT + MDIC's 5 compute prepasses + 3 render passes — opaque, temporal, translucent). `IOSurfaceBridgeCompositor` blits the bridge full-screen at HEAD of Sodium SOLID so MC's near terrain overdraws Voxy LOD via depth. User-confirmed: no diagnostic strip, LOD pyramid visible across the horizon, all chunks distinct face-shaded blocks.

The native library (`libvoxy_metal.dylib`) exposes ~100 JNI entry points.

**Each smoke test has caught at least one real bug.** The `VertexLayout` test caught an off-by-9 in our `MTLVertexFormat` enum mapping that the SDK header would have flagged. The ICB test caught a per-slot pipeline binding requirement. The runtime tested catches included GL 4.2 `glMemoryBarrier` slipping into `DownloadStream.commit` (caught on Apple's GL 4.1 context) and `MetalBuffer.id()` masquerading as a GL buffer name in `AsyncNodeManager`'s `glBindBufferRange` — both fixed via encoder-routing.

---

## What's still missing

M12 closed with one major gap: **no real model textures and no MC lightmap on Metal yet**, so LOD chunks render in a `VOXY_NO_ATLAS` debug mode (per-quad hash colour + sky-direction Lambertian shade). M13 fills that gap.

To finish M13 we need (roughly ordered by user-visible impact):

1. **Model texture atlas on Metal** (~1 day): `ModelTextureBakery` populates `ModelStore.textures` via raw GL FBO rendering using MC's block atlas (a `((GlTexture)tex).glId()` cross-cast that's GL-only by MC's design). Pragmatic path: keep the bakery on GL, then `glGetTexImage` → CPU buffer → upload to the MetalTexture at init. Atlas size ~400 MB; one-time cost. Or IOSurface-bridge the atlas (~1.5 days) for zero-copy + per-frame refresh ability.

2. **MC lightmap on Metal** (~0.5 day): same shape as the atlas but tiny (16×16 RGBA, ~1 KB). CPU readback per frame is negligible. Unblocks the `getLighting()` path in `quads.frag` so terrain lighting reads MC's actual time-of-day + torch state.

3. **MC depth import** (~1 day): bridge MC's depth attachment so `HiZBuffer.buildMipChain` can run for real, and the `force_all_visible` cull stub from M12 can be replaced by depth-test rasterized cull (the GL path's behaviour). Performance impact: occlusion-based culling probably cuts 50–90% of LOD draws in dense scenes.

4. **`finish()` blit + SSAO on Metal** (~0.5 day): once MC's depth is reachable on Metal, the compositor can become depth-aware (per-pixel test against MC's foreground instead of the M12 stacking workaround), and the post-opaque SSAO compute pass migrates trivially.

5. **Fog + atmosphere parity** (~0.5 day): the shader is already migrated; only the GL-only call site in `finish()` is missing.

Total estimate for M13: **~3-4 days** focused work.

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
