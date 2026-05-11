package me.cortex.voxy.client.core.gpu;

/**
 * Backend-agnostic handle for an indirect command buffer (Metal
 * {@code MTLIndirectCommandBuffer}, Vulkan-equivalent emulation, or a
 * Voxy-side draw-command buffer on OpenGL).
 *
 * Voxy's {@code MDICSectionRenderer} produces a packed list of
 * {@code DrawElementsIndirectCommand} structs in a regular SSBO, then
 * issues a single {@code glMultiDrawElementsIndirectCountARB} that reads
 * the count from a separate buffer. Metal has no native multi-draw-count
 * call — the closest is an MTLIndirectCommandBuffer plus
 * {@code executeCommandsInBuffer:indirectBuffer:indirectBufferOffset:},
 * which reads (range.location, range.length) from a Metal buffer at
 * encode time. Mapping the GL model onto that requires a compute prepass
 * to translate the GL-style command buffer into the Metal ICB and to
 * write a tiny "range" struct alongside.
 *
 * For now this interface is the resource handle; the encoder side will
 * gain {@link RenderEncoder#executeCommandsInBuffer} once
 * {@code MDICSectionRenderer} switches to the ICB path. On the GL
 * backend the implementation is intentionally minimal — GL has the
 * count-aware MDI API natively, so no ICB object is needed and the
 * backend's {@link RenderBackend#createIndirectCommandBuffer} returns a
 * thin wrapper.
 */
public interface IGpuIndirectCommandBuffer extends AutoCloseable {

    /** Maximum number of draws this ICB can hold (declared at creation). */
    int maxCommands();

    /** Optional debug label propagated to MTLIndirectCommandBuffer.label etc. */
    IGpuIndirectCommandBuffer name(String name);

    @Override
    void close();
}
