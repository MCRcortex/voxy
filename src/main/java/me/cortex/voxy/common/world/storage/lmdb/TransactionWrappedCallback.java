package me.cortex.voxy.common.world.storage.lmdb;

public interface TransactionWrappedCallback<T> {
    T exec(TransactionWrapper wrapper);
}
