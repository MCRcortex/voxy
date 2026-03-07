package me.cortex.voxy.client.core.metal;

import me.cortex.voxy.client.core.gpu.IGpuFence;
import me.cortex.voxy.common.util.TrackedObject;

/**
 * Metal implementation of IGpuFence using MTLSharedEvent.
 *
 * MTLSharedEvent provides a monotonically increasing counter that can be
 * signaled by the GPU and polled from the CPU. When a fence is created,
 * we encode a signal on the current command buffer to set the event to
 * a specific value, then poll for that value on the CPU side.
 */
public class MetalFence extends TrackedObject implements IGpuFence {
    private final long eventHandle;
    private final long targetValue;
    private boolean signaled;

    /**
     * Creates a fence that waits for the given shared event to reach targetValue.
     * The caller is responsible for encoding the signal on a command buffer.
     *
     * @param eventHandle native MTLSharedEvent handle
     * @param targetValue the value that signals completion
     */
    public MetalFence(long eventHandle, long targetValue) {
        this.eventHandle = eventHandle;
        this.targetValue = targetValue;
        MetalNative.mtlRetain(eventHandle);
    }

    @Override
    public boolean signaled() {
        if (!this.signaled) {
            long current = MetalNative.mtlSharedEventGetSignaledValue(this.eventHandle);
            if (current >= this.targetValue) {
                this.signaled = true;
            }
        }
        return this.signaled;
    }

    @Override
    public void free() {
        super.free0();
        MetalNative.mtlRelease(this.eventHandle);
    }
}
