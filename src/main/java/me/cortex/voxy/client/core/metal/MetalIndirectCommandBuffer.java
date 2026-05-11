package me.cortex.voxy.client.core.metal;

import me.cortex.voxy.client.core.gpu.IGpuIndirectCommandBuffer;

/**
 * Metal implementation of {@link IGpuIndirectCommandBuffer}. Wraps an
 * {@code MTLIndirectCommandBuffer} that holds up to {@code maxCommands}
 * pre-encoded draws. Population happens either CPU-side via
 * {@link MetalNative#mtlIndirectRenderCommandSetPipelineState} and friends,
 * or GPU-side via a compute prepass that emits commands using Metal's
 * indirect-command-buffer intrinsics — Voxy's MDIC will take the
 * compute-side path once {@code cmdgen.comp} is rewritten to target the
 * ICB instead of a flat {@code DrawElementsIndirectCommand} struct.
 *
 * Execution happens via {@link MetalRenderEncoder#executeCommandsInBuffer},
 * which Metal lowers to {@code executeCommandsInBuffer:indirectBuffer:
 * indirectBufferOffset:} — reading a (location, length) pair from a
 * separate Metal buffer at encode time so the GPU itself determines how
 * many of the ICB's slots actually render.
 */
public final class MetalIndirectCommandBuffer implements IGpuIndirectCommandBuffer {

    private final int maxCommands;
    private long handle;

    public MetalIndirectCommandBuffer(long device, int maxCommands) {
        this(device, maxCommands, /*inheritBuffers=*/true, /*inheritPipelineState=*/true);
    }

    public MetalIndirectCommandBuffer(long device, int maxCommands,
                                      boolean inheritBuffers, boolean inheritPipelineState) {
        this.maxCommands = maxCommands;
        // We currently only need indexed draws (MDIC's section quads). When
        // the prep / cull paths move to ICB-backed dispatches, OR additional
        // command-type bits in here.
        int commandTypes = MetalNative.MTLIndirectCommandTypeDrawIndexed;
        int options = 0;
        if (inheritBuffers)       options |= MetalNative.MTLIndirectCommandBufferOptionInheritBuffers;
        if (inheritPipelineState) options |= MetalNative.MTLIndirectCommandBufferOptionInheritPipelineState;
        this.handle = MetalNative.mtlDeviceNewIndirectCommandBuffer(device, commandTypes, maxCommands, options);
        if (this.handle == 0) {
            throw new RuntimeException(
                    "mtlDeviceNewIndirectCommandBuffer returned 0 for maxCommands=" + maxCommands);
        }
    }

    public long handle() {
        return this.handle;
    }

    @Override
    public int maxCommands() {
        return this.maxCommands;
    }

    @Override
    public IGpuIndirectCommandBuffer name(String name) {
        if (this.handle != 0 && name != null) {
            MetalNative.mtlSetLabel(this.handle, name);
        }
        return this;
    }

    /**
     * Reset commands [start, start+length) — call before re-populating the
     * ICB each frame so old commands don't bleed through.
     */
    public void reset(int rangeStart, int rangeLength) {
        if (rangeStart < 0 || rangeLength < 0 || rangeStart + rangeLength > this.maxCommands) {
            throw new IndexOutOfBoundsException(
                    "reset range [" + rangeStart + ", " + (rangeStart + rangeLength)
                            + ") out of bounds for maxCommands=" + this.maxCommands);
        }
        if (this.handle != 0) {
            MetalNative.mtlIndirectCommandBufferReset(this.handle, rangeStart, rangeLength);
        }
    }

    /**
     * Sanity-check a command index is in range. Returns true if the ICB has
     * a slot at this index. Useful for assertions; population goes through
     * the typed methods below.
     */
    public boolean hasCommand(int commandIndex) {
        if (this.handle == 0) return false;
        if (commandIndex < 0 || commandIndex >= this.maxCommands) return false;
        return MetalNative.mtlIndirectCommandBufferGetCommand(this.handle, commandIndex) != 0;
    }

    /** Bake an indexed-triangle draw into the given ICB slot. */
    public void encodeDrawIndexedPrimitives(int commandIndex,
                                            int metalPrimitiveType, int indexCount, int metalIndexType,
                                            long indexBufferHandle, long indexBufferOffset,
                                            int instanceCount, int baseVertex, int baseInstance) {
        checkIndex(commandIndex);
        MetalNative.mtlIndirectRenderCommandDrawIndexedPrimitives(
                this.handle, commandIndex,
                metalPrimitiveType, indexCount, metalIndexType,
                indexBufferHandle, indexBufferOffset,
                instanceCount, baseVertex, baseInstance);
    }

    /** Set the vertex buffer used by the given ICB slot. */
    public void encodeSetVertexBuffer(int commandIndex, long bufferHandle, long offset, int atIndex) {
        checkIndex(commandIndex);
        MetalNative.mtlIndirectRenderCommandSetVertexBuffer(
                this.handle, commandIndex, bufferHandle, offset, atIndex);
    }

    /** Set the fragment buffer used by the given ICB slot. */
    public void encodeSetFragmentBuffer(int commandIndex, long bufferHandle, long offset, int atIndex) {
        checkIndex(commandIndex);
        MetalNative.mtlIndirectRenderCommandSetFragmentBuffer(
                this.handle, commandIndex, bufferHandle, offset, atIndex);
    }

    /** Bake a PSO into the given ICB slot (only useful when the ICB was created without inheritPipelineState). */
    public void encodeSetPipelineState(int commandIndex, long psoHandle) {
        checkIndex(commandIndex);
        MetalNative.mtlIndirectRenderCommandSetPipelineState(this.handle, commandIndex, psoHandle);
    }

    private void checkIndex(int commandIndex) {
        if (commandIndex < 0 || commandIndex >= this.maxCommands) {
            throw new IndexOutOfBoundsException(
                    "commandIndex=" + commandIndex + " out of bounds for maxCommands=" + this.maxCommands);
        }
    }

    @Override
    public void close() {
        if (this.handle != 0) {
            MetalNative.mtlRelease(this.handle);
            this.handle = 0;
        }
    }
}
