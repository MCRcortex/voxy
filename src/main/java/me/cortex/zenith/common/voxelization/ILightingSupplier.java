package me.cortex.zenith.common.voxelization;

import net.minecraft.block.BlockState;

public interface ILightingSupplier {
    byte supply(int x, int y, int z, BlockState state);
}
