package me.cortex.neovoxy.common.config.storage.lmdb;

public interface TransactionWrappedCallback<T> {
    T exec(TransactionWrapper wrapper);
}
