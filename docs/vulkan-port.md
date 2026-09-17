# Vulkan port assessment — Minecraft 26.3

Status: **not implemented**. The current 26.3 port renders LoDs with OpenGL.
The backend check in `VoxyClient.initVoxyClient` prevents OpenGL initialization
on Vulkan. Disabling Voxy there is not Vulkan rendering support.

## Findings from the 26.3 game sources

Minecraft's graphics classes now live under `com.mojang.renderpearl`.
The public `api.pipeline.ShaderType` exposes vertex and fragment stages only.
`api.commands.CommandEncoder` has no compute-dispatch API. Using this API alone
cannot preserve Voxy's GPU-driven occlusion traversal and indirect draw generation.

The native Vulkan backend has usable starting points: `VulkanDevice.vkDevice()`,
`vma()`, `graphicsQueue()` and `computeQueue()`. Integrating directly requires
access to the frontend's backend plus careful coordination with Minecraft's
command recording, image layouts, submissions and resource lifetime. It is not
safe to submit independent commands against Minecraft textures without that coordination.

There are 43 production Java files importing LWJGL OpenGL. Key dependencies are:

| Area | Existing implementation | Vulkan work |
| --- | --- | --- |
| Buffers, uploads, readbacks | `core/gl`, `UploadStream`, `DownloadStream` | Vulkan/VMA allocations, mapped staging rings and fence-based retirement |
| Shader compilation | `core/gl/shader` | SPIR-V compilation, descriptor layouts and graphics/compute pipelines |
| Visibility and LoD traversal | `HierarchicalOcclusionTraverser`, `NodeCleaner`, `AsyncNodeManager` | Compute pipelines, storage buffers, indirect dispatch and explicit barriers |
| Geometry drawing | `MDICSectionRenderer` | Indexed indirect-count draws, descriptor binding and feature checks |
| Model atlas and lighting | `SoftwareModelTextureBakery`, `LightMapHelper` | GPU image readback and sampling without OpenGL texture IDs |
| Depth and compositing | `VoxyRenderSystem`, rendering pipelines, SSAO | Shared depth conventions, render attachments, layout transitions and compute passes |
| Optional shaders | Iris integration mixins | Separate compatibility work; OpenGL Iris hooks cannot be reused as-is |

## Implementation sequence

1. Introduce an explicit backend boundary for GPU resources and frame execution,
   retaining the working OpenGL implementation as a regression reference.
2. Add a Vulkan device bridge and a minimal offscreen triangle test sharing
   Minecraft's device and submission lifecycle. Check with Vulkan validation layers.
3. Port buffer allocation, uploads/readbacks and shader compilation; validate a
   compute dispatch with known input/output before porting the traversal algorithms.
4. Port the MDIC path and its dependencies (visibility, indirect draw generation,
   model atlas, lighting, depth pyramid and composition).
5. Test actual LoD output, world/dimension changes, resizing, resource reloads,
   shutdown, and GPU-memory lifetime. Only then enable Vulkan as a supported backend.

No Vulkan support claim should be made on the basis of a successful Java build
or a game launch where Voxy has disabled itself.
