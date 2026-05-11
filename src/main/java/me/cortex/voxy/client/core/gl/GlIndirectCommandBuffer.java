package me.cortex.voxy.client.core.gl;

import me.cortex.voxy.client.core.gpu.IGpuIndirectCommandBuffer;

/**
 * OpenGL implementation of {@link IGpuIndirectCommandBuffer}.
 *
 * GL has no native indirect-command-buffer object — multi-draw-indirect-count
 * is a flat call against existing buffer bindings. So this implementation is
 * essentially a metadata holder: it records the max-command count so the
 * encoder's {@code executeCommandsInBuffer} call can validate it, but
 * carries no GPU resource of its own.
 */
public final class GlIndirectCommandBuffer implements IGpuIndirectCommandBuffer {

    private final int maxCommands;
    private String label;
    private boolean closed;

    public GlIndirectCommandBuffer(int maxCommands) {
        this.maxCommands = maxCommands;
    }

    @Override
    public int maxCommands() {
        return this.maxCommands;
    }

    @Override
    public IGpuIndirectCommandBuffer name(String name) {
        this.label = name;
        return this;
    }

    @Override
    public void close() {
        this.closed = true;
    }
}
