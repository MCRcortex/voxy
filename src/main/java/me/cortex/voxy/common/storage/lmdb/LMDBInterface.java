package me.cortex.voxy.common.storage.lmdb;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.lmdb.MDBEnvInfo;

import java.nio.IntBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.lmdb.LMDB.*;

public class LMDBInterface {
    private final long env;
    private LMDBInterface(long env) {
        this.env = env;
    }

    public static class Builder {
        private final long env;
        public Builder() {
            //Create the environment
            try (var stack = stackPush()) {
                PointerBuffer pp = stack.mallocPointer(1);
                E(mdb_env_create(pp));
                this.env = pp.get(0);
            }
        }

        public Builder setMaxDbs(int maxDbs) {
            E(mdb_env_set_maxdbs(this.env, maxDbs));
            return this;
        }

        public Builder open(String directory, int flags) {
            E(mdb_env_open(this.env, directory, flags, 0664));
            return this;
        }

        public LMDBInterface fetch() {
            return new LMDBInterface(this.env);
        }
    }

    public void close() {
        mdb_env_close(env);
    }

    public static void E(int rc) {
        if (rc != MDB_SUCCESS) {
            throw new IllegalStateException("Code: " + rc + " msg: " + mdb_strerror(rc));
        }
    }

    public void setMapSize(long size) {
        E(mdb_env_set_mapsize(this.env, size));
    }

    public <T> T transaction(TransactionCallback<T> transaction) {
        return transaction(0, transaction);
    }

    public <T> T transaction(int flags, TransactionCallback<T> transaction) {
        return transaction(0, flags, transaction);
    }

    public <T> T transaction(long parent, int flags, TransactionCallback<T> transaction) {
        T ret;
        try (var stack = stackPush()) {
            PointerBuffer pp = stack.mallocPointer(1);
            E(mdb_txn_begin(this.env, parent, flags, pp));
            long txn = pp.get(0);
            int err;
            try {
                ret = transaction.exec(stack, txn);
                err = mdb_txn_commit(txn);
            } catch (Throwable t) {
                mdb_txn_abort(txn);
                throw t;
            }
            E(err);
        }
        return ret;
    }

    public Database createDb(String name) {
        return this.createDb(name, MDB_CREATE|MDB_INTEGERKEY);
    }

    public Database createDb(String name, int flags) {
        return new Database(name, flags);
    }

    public LMDBInterface flush(boolean force) {
        E(mdb_env_sync(this.env, force));
        return this;
    }

    public long getMapSize() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            MDBEnvInfo info = MDBEnvInfo.calloc(stack);
            E(mdb_env_info(this.env, info));
            return info.me_mapsize();
        }
    }

    public class Database {
        private final int dbi;
        public Database(String name, int flags) {
            this.dbi = LMDBInterface.this.transaction((stack, txn)-> {
                IntBuffer ip = stack.mallocInt(1);
                E(mdb_dbi_open(txn, name, flags, ip));
                return ip.get(0);
            });
        }

        public void close() {
            mdb_dbi_close(LMDBInterface.this.env, this.dbi);
        }

        //TODO: make a MDB_RDONLY varient
        public <T> T transaction(TransactionWrappedCallback<T> callback) {
            return this.transaction(0, callback);
        }

        public <T> T transaction(int flags, TransactionWrappedCallback<T> callback) {
            return LMDBInterface.this.transaction(flags, (stack, transaction) -> {
                return callback.exec(new TransactionWrapper(transaction, stack).set(this));
            });
        }

        public int getDBI() {
            return this.dbi;
        }
    }

}
