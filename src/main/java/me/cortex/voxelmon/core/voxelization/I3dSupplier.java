package me.cortex.voxelmon.core.voxelization;

public interface I3dSupplier <T> {
    T supply(int x, int y, int z);
}
