package me.cortex.voxy.common.storage.lmdb;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.lmdb.MDBVal;

import java.nio.ByteBuffer;

import static me.cortex.voxy.common.storage.lmdb.LMDBInterface.E;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.lmdb.LMDB.*;

public class TransactionWrapper {
    public final MemoryStack stack;
    private final long transaction;
    private int dbi;

    public TransactionWrapper(long transaction, MemoryStack stack) {
        this.transaction = transaction;
        this.stack = stack;
    }

    public TransactionWrapper set(LMDBInterface.Database db) {
        this.dbi = db.getDBI();
        return this;
    }

    public TransactionWrapper put(ByteBuffer key, ByteBuffer val, int flags) {
        try (var stack = stackPush()) {
            E(mdb_put(this.transaction, this.dbi, MDBVal.malloc(stack).mv_data(key), MDBVal.malloc(stack).mv_data(val), flags));
            return this;
        }
    }

    public TransactionWrapper del(ByteBuffer key) {
        try (var stack = stackPush()) {
            E(mdb_del(this.transaction, this.dbi, MDBVal.malloc(stack).mv_data(key), null));
            return this;
        }
    }

    /*
    public TransactionWrapper put(long keyPtr, long keyLen, long valPtr, int valLen, int flags) {
        //TODO: instead give TransactionWrapper its own scratch buffer that it can use
        try (var stack = stackPush()) {
            long ptr = stack.nmalloc(4*8);
            MemoryUtil.memPutLong(ptr, keyPtr);
            MemoryUtil.memPutLong(ptr+8, keyLen);
            MemoryUtil.memPutLong(ptr+16, valPtr);
            MemoryUtil.memPutLong(ptr+24, valLen);
            E(nmdb_put(this.transaction, this.dbi, ptr, ptr + 16, flags));
            return this;
        }
    }*/

    public ByteBuffer get(ByteBuffer key) {
        //TODO: instead give TransactionWrapper its own scratch buffer that it can use
        try (var stack = stackPush()) {
            var ret = MDBVal.malloc(stack);
            int retVal = mdb_get(this.transaction, this.dbi, MDBVal.calloc(stack).mv_data(key), ret);
            if (retVal == MDB_NOTFOUND) {
                return null;
            } else {
                E(retVal);
            }
            return ret.mv_data();
        }
    }

    public Cursor createCursor() {
        try (var stack = stackPush()) {
            PointerBuffer pb = stack.mallocPointer(1);
            E(mdb_cursor_open(transaction, dbi, pb));
            return new Cursor(pb.get(0));
        }
    }
}
