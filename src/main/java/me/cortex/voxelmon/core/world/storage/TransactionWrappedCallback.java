package me.cortex.voxelmon.core.world.storage;

public interface TransactionWrappedCallback<T> {
    T exec(TransactionWrapper wrapper);
}
