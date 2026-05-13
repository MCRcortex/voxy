# Voxy → Mac M-Series Port — Status

**Last update:** 2026-05-12
**Branch:** `claude/opengl-mac-migration-analysis-6319V`
**HEAD:** `a007f038`
**Commits ahead of `dev`:** 81

> One-page summary of where the port stands and what's next. Detailed
> commit-by-commit history + open-decision logs live in
> [`M-SERIES-PORT-STATE.md`](M-SERIES-PORT-STATE.md). Higher-level
> narrative in [`M-SERIES-PORT-OVERVIEW.md`](M-SERIES-PORT-OVERVIEW.md).

---

## Current state

**Milestones M0–M12 closed.** Voxy renders LOD chunks end-to-end on
Apple M4 (macOS 26.4.1, JDK 24) inside Minecraft via `VOXY_FORCE_METAL=1`.
User-confirmed visually 2026-05-12: full-screen LOD pyramid behind
Sodium's near terrain, every chunk visibly distinct, no diagnostic
overlay strip.

### What works on Metal today

- **Bootstrap** — Metal device + command queue + shared event; LWJGL
  natives bundled for macOS arm64 (`lwjgl-metal`, `lwjgl-zstd`,
  `lwjgl-lmdb`).
- **MC lightmap on Metal** (M13 chunk 2) — Voxy keeps a Shared-storage
  16×16 RGBA8 mirror of MC's lightmap; `LightMapHelper.bindMetal`
  CPU-reads MC's GL lightmap via `glGetTexImage` and pushes it into
  the mirror once per frame (gated by `viewport.frameId` so opaque +
  temporal + translucent share one upload). The mirror is bound at
  `LIGHTING_SAMPLER_BINDING = 1` on the render encoder alongside a
  LINEAR / CLAMP_TO_EDGE sampler. `quads3.vert`'s `getLighting()`
  now reads real MC sky/block-light values on Metal, and the
  VOXY_NO_ATLAS path in `quads.frag` modulates its per-quad hash
  colour by the lightmap × face-shade colour packed into
  `interData.y` (synthetic face-Lambertian shade is gone).
- **Cross-backend abstraction** — `RenderBackend` / `RenderEncoder` /
  `ComputeEncoder` real on Metal *and* OpenGL (Win/Linux still works).
  Pipeline state (depth + blend + raster), samplers, push-constants-
  equivalent, indirect command buffers (ICB).
- **Shader translation** — runtime GLSL → SPIRV (`shaderc`) → MSL
  (`spvc`) with disk cache. 28/28 SPIRV smoke cases pass; 27/28 MSL
  (`hiz.comp` still defers — uses `subgroupClusteredAdd` with cluster
  > 4, which Metal's spvc-MSL doesn't support).
- **IOSurface bridge** — IOSurface wrapped as both `MTLTexture`
  (render target) and `GL_TEXTURE_RECTANGLE` (sampleable from MC's
  GL compositor). Used to surface Voxy's Metal render into MC's
  framebuffer per-frame.
- **Voxy compute pipeline on Metal** — full chain runs end-to-end:
  `AsyncNodeManager.tick`, `NodeCleaner.tick`, `HierarchicalOcclusion-
  Traverser.doTraversal`, MDIC's 5 prepasses (`prep`, `cull` (Metal
  stub via `force_all_visible.comp`), `commandGen`, `prefixSum`,
  `translucentGen`). HOT and ANM/NC are fully backend-agnostic — no
  raw GL on the hot path.
- **Voxy render pipeline on Metal** — `runPipelineMetal` opens a
  render pass against bridge color + Voxy-owned depth texture, then
  invokes `renderOpaqueMetal → renderTemporalMetal → renderTranslucent-
  Metal` (3-pass MDIC). Each pass binds the 6 SSBOs through
  `encoder.setBuffer` + the shared `SharedIndexBuffer` as UINT16
  index + `drawIndexedIndirect`.
- **Compositor** — `IOSurfaceBridgeCompositor` blits the bridge
  full-screen into MC's main RT at HEAD of Sodium's SOLID pass, so
  Sodium's near terrain overdraws Voxy LOD via depth-test naturally.
- **Chunk persistence** — works on Mac since M11 (LMDB + zstd
  natives + background workers; orthogonal to render).
- **Clean shutdown** — `AsyncNodeManager` worker is daemon-flagged;
  MC's close button exits the JVM without hanging.

### What's NOT yet working on Metal

- **Real model textures (M13 chunk 1 on hold).** `ModelTextureBakery`
  is now **auto-gated to a no-op on Metal** — set
  `VOXY_BAKERY_FORCE=1` to override for debugging. The hold has two
  layered Apple GL blockers: (1) `glReadPixels` / `glGetTexImage` on
  FBO attachments crash inside Apple's
  `glgVectorCopy / glgProcessPixelsWithProcessor` (SIGBUS BUS_ADRALN
  on the *second* bake invocation — hs_err_pid73201/73394/73671).
  Mitigations landed (persistent per-instance scratch + per-call
  `glFinish`) make readback survive long enough to expose (2)
  Sodium's `SharedQuadIndexBuffer.grow → glMapBufferRange` returns
  null mid-frame whenever the GL bakery has run, raising
  `RuntimeException: Failed to map buffer` even after the bakery
  restores VAO/program/FBO/sampler/UBO state. Bisect proof:
  `VOXY_BAKERY_OFF=1` → game runs 19,800+ frames clean; bakery on →
  Sodium dies on the very first chunk batch. The atlas needs a
  Metal-native bakery (replacing the FBO-render approach entirely),
  which is its own milestone. Until then, `VOXY_NO_ATLAS` continues
  to render LOD with the per-quad hash colour × MC lightmap × face-
  shade × procedural checker pattern.
- **Real occlusion cull.** The M12 `force_all_visible` compute stub
  marks every frustum-visible section as "visible this frame", so
  `commandGen` queues them all for rendering. No depth-test rejection
  of occluded sections → more overdraw than the GL path would have.
  Functionally correct, just slower in dense scenes.
- **HiZ depth pyramid.** `HiZBuffer.buildMipChain` is migrated but
  needs the source depth tex bound; on Metal there's no cross-
  context MC-depth handle yet, so we pass `0` for the source and
  HiZBuffer's `ensureAllocated` reserves a zero-init pyramid. HOT
  reads this as "everything passes the HiZ test" → no early
  rejection (slower, functionally correct).
- **`finish()` + SSAO + final blit.** Skipped on Metal. The
  IOSurfaceBridgeCompositor's full-screen blit substitutes (lossy:
  no per-pixel depth-test against MC's foreground in the blit
  itself). Sodium overdrawing on top via its own depth keeps the
  visual stacking right for now.

### How to test

```bash
VOXY_FORCE_METAL=1 ./gradlew runClient
```

Expected: load a world, walk past MC's render distance, look at the
horizon. Sodium chunks render normally up close; beyond that you
see Voxy's LOD pyramid — every chunk a distinct face-shaded block
with the procedural checker+speckle pattern. No diagnostic strip;
no crashes; clean close on quit.

For the GL backend (Win/Linux, or Mac without the env var):
unchanged behaviour — Voxy renders LOD with real Minecraft textures
via the existing GL pipeline.

### Smoke tests (all green on Apple M4 Max)

```bash
./gradlew testShaderCompiler    # 28/28 SPIRV, 27/28 MSL (hiz.comp deferred)
./gradlew testMetalRender       # M3 — clear-color render pass
./gradlew testMetalTriangle     # M5 — graphics pipeline + draw
./gradlew testMetalCompute      # M7 — compute pipeline + dispatch
./gradlew testMetalVertexBuffer # M9-prep — vertex layout + drawIndirect
./gradlew testMetalIcb          # Blocker 1 — MTLIndirectCommandBuffer
./gradlew testIOSurfaceBridge   # M10 — IOSurface + MTLTexture wrap
./gradlew testVulkanLoader      # M4-A — MoltenVK + VkInstance
./gradlew testVulkanClear       # M4-D — dynamic_rendering clear
./gradlew testVulkanTriangle    # M6 — Vulkan graphics + draw
./gradlew testVulkanCompute     # M8 — Vulkan compute + descriptor sets
```

---

## Next steps — M13 (texture / lighting / depth-import polish)

**Goal:** Voxy on Metal visually indistinguishable from the GL backend
(modulo Iris features, which stay GL-gated).

Chunk 2 closed 2026-05-12 — `IGpuTexture.uploadSubImage2D` primitive
+ Shared-storage `MetalTexture.storeUploadable` + per-frame MC
lightmap mirror in `LightMapHelper.bindMetal`. Remaining chunks are
independent and can be done in any order.

| # | Chunk | Estimate | What it unlocks |
|---|---|---|---|
| 1 | **Model texture atlas on Metal** *(on hold — needs Metal-native bakery)* | re-scoped | Real Minecraft block textures (drops `VOXY_NO_ATLAS`). GL-FBO bakery proven incompatible with Apple GL stack on Metal — see "what's NOT working" above. |
| 2 | ✅ **MC lightmap on Metal** (closed 2026-05-12) | done | Proper time-of-day + torch lighting on LOD chunks. Built the cross-backend `IGpuTexture.uploadSubImage2D` primitive (chunk 1 will reuse). |
| 3 | **MC depth import (real HiZ + real cull)** | ~1 day, 1–2 turns | Real occlusion culling — perf win in dense scenes (50–90% fewer draws). Replaces M12's `force_all_visible` stub. |
| 4 | **`finish()` blit + SSAO on Metal** | ~0.5 day, 1 turn | Depth-aware compositor (replaces the M12 full-screen workaround), SSAO ambient occlusion on LOD. |
| 5 | **Fog + atmosphere parity** | ~0.5 day, 1 turn | LOD chunks fade with distance fog matching MC's near-terrain fog. Lands together with chunk 4. |

**Total M13 scope remaining:** ~3 days of focused work.

### Chunk 1 — atlas detail

`ModelTextureBakery` populates `ModelStore.textures` via raw GL FBO
rendering + a compute-shader readback (`bufferreorder.comp`). Three
approaches in increasing order of work:

- **(a) CPU readback bridge** (~1 day, recommended starter): keep
  the bakery on GL, replace the compute readback with
  `glGetTexImage`, route the final upload via a new
  `IGpuTexture.uploadSubImage2D` (cross-backend). Apple GL 4.1 has
  `glGetTexImage` natively; the bakery's bake-side fragment
  shaders work on 4.1 too. Atlas is ~12k × 8k RGBA8 ≈ 400 MB; the
  CPU-readback path adds ~1 s to init.
- **(b) IOSurface atlas bridge** (~1.5 days): allocate an IOSurface
  sized to the atlas, wrap as `GL_TEXTURE_RECTANGLE` on GL and
  `MTLTexture` on Metal, bake into it from GL, sample from Metal.
  Zero-copy after init. Complication: `GL_TEXTURE_RECTANGLE`
  doesn't support mipmaps and uses unnormalized UVs; the terrain
  shader uses normalized + `textureGrad`. Either drop mipmaps
  (visible quality loss at distance) or work around the binding
  shape.
- **(c) Full bakery encoder migration** (~2–3 days): port the
  entire bakery + `GlViewCapture` + the residual raw GL in
  `BudgetBufferRenderer` to the `RenderEncoder` abstraction, plus
  cross-context MC-atlas via IOSurface. Cleanest long-term but
  biggest scope.

### Chunk 2 — lightmap detail (✅ closed 2026-05-12)

MC's lightmap is 16×16 RGBA8 = 1 KB. Per-frame CPU readback
(`glGetTexImage`) is trivially cheap. Implementation:

- New `IGpuTexture.uploadSubImage2D(level, x, y, w, h, format, type, addr)`
  cross-backend primitive. GL: `GLCompat.textureSubImage2D`.
  Metal: `MetalNative.mtlTextureReplaceRegion` — gated on
  Shared/Managed storage mode so callers can't accidentally upload
  into a Private render-target texture.
- New `MetalTexture.storeUploadable(format, levels, w, h)` allocates
  with `MTLStorageModeShared` + `MTLTextureUsageShaderRead` (drops
  the RenderTarget usage flag — Apple Silicon would otherwise
  warn about eviction overhead on a Shared render target).
- `LightMapHelper.bindMetal(encoder, slot, frameId)` lazy-allocates
  the mirror + a LINEAR / CLAMP_TO_EDGE sampler, CPU-reads MC's GL
  lightmap into a 1 KB pinned staging buffer via the bind-then-
  `nglGetTexImage` legacy path (Apple's GL 4.1 lacks
  `glGetTextureImage`), and pushes it into the mirror.
  Frame-id-gated so the three terrain passes share one readback.
- `MDICSectionRenderer.renderTerrainMetal` calls
  `LightMapHelper.bindMetal(encoder, 1, viewport.frameId)` — binding
  slot 1 matches `LIGHTING_SAMPLER_BINDING` in `quads3.vert`.
- `quads.frag` (VOXY_NO_ATLAS path) now modulates the per-quad hash
  colour by `uint2vec4RGBA(interData.y).rgb` — the lightmap ×
  face-shade colour the vertex shader packed via `makeRemainingAttributes`.
  The synthetic face-Lambertian shade from M12 is removed (the
  vertex-shader path now bakes real sky/block-light values × MC's
  level.getShade per face). The procedural checker + speckle pattern
  stays as the per-pixel detail layer until chunk 1 lands.

### Chunk 3 — depth import detail

MC's main RT depth is `GL_DEPTH24_STENCIL8`. IOSurface supports
D24S8 on macOS, so direct bridging works (same shape as the
existing color bridge — different format constant). Adds the
bridged depth tex as the source for `HiZBuffer.buildMipChain`,
which then populates the HiZ pyramid for real. HOT's traversal
gets real occlusion data, `commandGen` rejects occluded sections,
and the `force_all_visible.comp` Metal stub can be dropped.

### Chunk 4 — `finish()` + SSAO detail

Once depth is reachable on Metal (chunk 3), the compositor can do
a depth-aware blit instead of the M12 full-screen workaround.
SSAO is a pure compute on color + depth — straightforward encoder
migration once depth is available.

### Chunk 5 — fog detail

The fog math in `blit_texture_depth_cutout.frag` already compiles
to MSL — only the GL-only call site in `NormalRenderPipeline.finish`
is missing. Lands together with chunk 4.

---

## Open decisions

- **Atlas approach** (chunk 1): (a) CPU readback vs (b) IOSurface
  bridge. (a) is simpler but adds ~1 s init time; (b) is zero-copy
  but loses mipmaps. Recommendation: try (a) first, measure init
  time, decide if (b)'s engineering work is worth it.
- **Lightmap freshness** (chunk 2): MC's lightmap changes every
  frame (sky / torch light state). Per-frame readback or per-frame
  IOSurface re-sync. CPU readback is trivial at 1 KB; IOSurface
  bridge has no real advantage at this size.
- **Depth format** (chunk 3): direct bridge of MC's D24S8, or
  copy-resolve to a Metal-private depth-only texture. Direct bridge
  is simpler if IOSurface supports D24S8 on this macOS version.

---

## Git status

- All 81 commits authored as `Jeferson Argueta
  <ajefersonstiv@gmail.com>` (after the 2026-05-12 author-email
  rewrite via `git filter-branch`; backup ref preserved at
  `refs/original/refs/heads/claude/opengl-mac-migration-analysis-6319V`).
- **Branch has not been pushed.** Auth on this machine resolves to
  GitHub user `jeferson-argueta-kernel` which doesn't have write
  access to `srjefers/voxy-mseries-support`. Resolution: `gh auth
  login` with an account that owns / collaborates on the repo, or
  add `jeferson-argueta-kernel` as a collaborator on the repo
  settings. Once auth is fixed, push with `--force-with-lease`
  (history was rewritten so the existing remote head differs).

---

## Quick links

- [Detailed history + commit table](M-SERIES-PORT-STATE.md)
- [Narrative overview](M-SERIES-PORT-OVERVIEW.md)
- M12 closure commit: `75277c42`
- M12 doc-closure commit: `e4123bb4`
- Latest polish: `a007f038` (procedural checker+speckle in
  `VOXY_NO_ATLAS`)
