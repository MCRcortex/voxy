package me.cortex.voxelmon.common.voxelization;

public interface I3dSupplier <T> {
    T supply(int x, int y, int z);
}
