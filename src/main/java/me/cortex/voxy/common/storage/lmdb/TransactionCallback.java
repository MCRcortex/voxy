package me.cortex.voxy.common.storage.lmdb;

import org.lwjgl.system.MemoryStack;

public interface TransactionCallback<T> {
    T exec(MemoryStack stack, long transaction);
}
