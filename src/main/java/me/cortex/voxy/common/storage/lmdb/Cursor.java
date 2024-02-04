package me.cortex.voxy.common.storage.lmdb;

import org.lwjgl.util.lmdb.MDBVal;

import static me.cortex.voxy.common.storage.lmdb.LMDBInterface.E;
import static org.lwjgl.util.lmdb.LMDB.*;

public class Cursor implements AutoCloseable {
    private final long cursor;
    public Cursor(long cursor) {
        this.cursor = cursor;
    }

    public int get(int op, MDBVal key, MDBVal data) {
        int e = mdb_cursor_get(this.cursor, key, data, op);
        if (e != MDB_SUCCESS && e != MDB_NOTFOUND) {
            E(e);
        }
        return e;
    }

    @Override
    public void close() {
        mdb_cursor_close(this.cursor);
    }
}
