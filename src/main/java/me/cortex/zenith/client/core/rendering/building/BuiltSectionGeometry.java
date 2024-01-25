package me.cortex.zenith.client.core.rendering.building;

import me.cortex.zenith.common.util.MemoryBuffer;

import java.util.Arrays;

/**
 * @param startOffsets Will be converted to ending offsets when doing data computation
 */
public record BuiltSectionGeometry(MemoryBuffer buffer, short[] startOffsets) {

    public BuiltSectionGeometry clone() {
        return new BuiltSectionGeometry(this.buffer != null ? this.buffer.copy() : null, Arrays.copyOf(this.startOffsets, this.startOffsets.length));
    }

    public void free() {
        if (this.buffer != null) {
            this.buffer.free();
        }
    }
}
