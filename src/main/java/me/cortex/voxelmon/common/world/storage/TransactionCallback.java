package me.cortex.voxelmon.common.world.storage;

import org.lwjgl.system.MemoryStack;

public interface TransactionCallback<T> {
    T exec(MemoryStack stack, long transaction);
}
