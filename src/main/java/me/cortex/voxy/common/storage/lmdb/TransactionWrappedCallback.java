package me.cortex.voxy.common.storage.lmdb;

public interface TransactionWrappedCallback<T> {
    T exec(TransactionWrapper wrapper);
}
