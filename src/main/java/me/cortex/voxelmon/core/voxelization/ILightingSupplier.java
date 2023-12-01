package me.cortex.voxelmon.core.voxelization;

import net.minecraft.block.BlockState;

public interface ILightingSupplier {
    byte supply(int x, int y, int z, BlockState state);
}
