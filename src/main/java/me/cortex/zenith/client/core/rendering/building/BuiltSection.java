package me.cortex.zenith.client.core.rendering.building;

import java.util.Objects;

//TODO: also have an AABB size stored
public final class BuiltSection {
    public final long position;
    public final BuiltSectionGeometry opaque;
    public final BuiltSectionGeometry translucent;

    public BuiltSection(long position, BuiltSectionGeometry opaque, BuiltSectionGeometry translucent) {
        this.position = position;
        this.opaque = opaque;
        this.translucent = translucent;
    }

    public BuiltSection clone() {
        return new BuiltSection(this.position, this.opaque != null ? this.opaque.clone() : null, this.translucent != null ? this.translucent.clone() : null);
    }

    public void free() {
        if (this.opaque != null) {
            this.opaque.free();
        }
        if (this.translucent != null) {
            this.translucent.free();
        }
    }
}
