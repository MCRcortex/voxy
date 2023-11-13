package me.cortex.voxelmon.core.world.storage;

import org.lwjgl.system.MemoryStack;

public interface TransactionCallback<T> {
    T exec(MemoryStack stack, long transaction);
}
