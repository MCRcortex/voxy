package me.cortex.voxy.common.voxelization;

public interface I3dSupplier <T> {
    T supply(int x, int y, int z);
}
