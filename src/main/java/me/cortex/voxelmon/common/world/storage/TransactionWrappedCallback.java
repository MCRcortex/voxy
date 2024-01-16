package me.cortex.voxelmon.common.world.storage;

public interface TransactionWrappedCallback<T> {
    T exec(TransactionWrapper wrapper);
}
