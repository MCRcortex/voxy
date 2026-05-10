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

We're somewhere between M8 and M9 today.

---

## Where we are now

**12 commits on the branch. All 9 smoke tests pass on this Apple M4 Max.**

- ✅ Dev env (JDK 24, Xcode CLT, CMake) verified.
- ✅ Runtime shader compiler — translates Voxy's GLSL to SPIRV (for Vulkan) and to MSL (for Metal) on the fly. Disk cache. 9/9 representative shaders compile, including the heavy compute ones.
- ✅ Encoder API — Voxy can describe a render or compute pass through a backend-agnostic API instead of raw GL.
- ✅ **Metal**: clear color, triangle, compute dispatch + SSBO write all working end-to-end on the GPU. Vertex layout + indirect draw working.
- ✅ **Vulkan via MoltenVK**: same — clear color, triangle, compute dispatch with descriptor sets all working.
- ✅ Pipeline state config (depth, blend, cull, polygon mode), samplers, push-constant-equivalent uniforms — all wired up.

The native library (`libvoxy_metal.dylib`) exposes ~85 JNI entry points covering most of what Voxy will need.

**Each smoke test has caught at least one real bug.** The `VertexLayout` test caught an off-by-9 in our `MTLVertexFormat` enum mapping that the SDK header would have flagged — without that test, we'd have hit it during M9 with much more code on top.

---

## What's still missing

For Voxy to actually run a frame in Minecraft on Mac, four blockers remain:

1. **ICB (`MTLIndirectCommandBuffer`)** — Voxy's main render path uses `glMultiDrawElementsIndirectCountARB` (multi-draw with the draw count coming from a GPU buffer). Metal lacks a native equivalent; we need to use Indirect Command Buffers and rewrite the `cmdgen.comp` compute shader to populate them. Estimate: **~1 day**.

2. **GL backend stubs the new abstraction** — every new method in the encoder API throws `UnsupportedOperationException` on the GL backend with a "see M9" note. To migrate any Voxy file without breaking Win/Linux GL users, the GL backend needs to implement the same abstraction (just with different underlying GL calls). Estimate: **~1–2 days**.

3. **Per-mip texture views** — Voxy's HiZ pass binds different mipmap levels of the same texture as separate storage images. Metal needs explicit "texture views" for each level, and our `IGpuTexture.createView()` doesn't take a mip-level argument yet. Estimate: **~1–2 hours**.

4. **`hiz.comp` MSL workaround** — the HiZ compute shader uses subgroup operations with cluster size 32. SPIRV-Cross's MSL backend only supports cluster size 4. Vulkan-via-MoltenVK runs it natively, but Metal direct needs either a hand-written MSL replacement or the algorithm rewritten to use only quad-size subgroups. Estimate: **~0.5–1 day**.

Plus a handful of **shader source patches** (extension declarations, version bumps) and one **shader topology tweak** (Metal has no `GL_TRIANGLE_FAN`). Each ~30 min.

After those blockers, **M9 itself is the file-by-file migration of Voxy's render code**, recommended order:

1. Util compute callers (smallest, easiest)
2. `HiZBuffer2`
3. `HierarchicalOcclusionTraverser`
4. `NormalRenderPipeline`, `VoxyRenderSystem`, `AbstractRenderPipeline`
5. `MDICSectionRenderer` (the central, hardest piece — needs ICB + everything else)

Each is a separate commit; each should keep the repo green. Total estimate for M9: **~4–5 days** assuming the blockers above are cleared first.

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
