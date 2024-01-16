package me.cortex.voxelmon.client.core.rendering.building;

import me.cortex.voxelmon.common.util.MemoryBuffer;
import me.cortex.voxelmon.common.world.WorldEngine;

public class BuiltSectionGeometry {
    public final long position;
    public final MemoryBuffer geometryBuffer;
    public final MemoryBuffer translucentGeometryBuffer;

    public BuiltSectionGeometry(int lvl, int x, int y, int z, MemoryBuffer geometryBuffer, MemoryBuffer translucentGeometryBuffer) {
        this(WorldEngine.getWorldSectionId(lvl, x, y, z), geometryBuffer, translucentGeometryBuffer);
    }
    public BuiltSectionGeometry(long position, MemoryBuffer geometryBuffer, MemoryBuffer translucentGeometryBuffer) {
        this.position = position;
        this.geometryBuffer = geometryBuffer;
        this.translucentGeometryBuffer = translucentGeometryBuffer;
    }

    public void free() {
        if (this.geometryBuffer != null) {
            this.geometryBuffer.free();
        }
        if (this.translucentGeometryBuffer != null) {
            this.translucentGeometryBuffer.free();
        }
    }

    public BuiltSectionGeometry clone() {
        return new BuiltSectionGeometry(this.position, this.geometryBuffer!=null?this.geometryBuffer.copy():null, this.translucentGeometryBuffer!=null?this.translucentGeometryBuffer.copy():null);
    }
}
