package me.cortex.voxy.common.storage.lmdb;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.storage.StorageBackend;
import me.cortex.voxy.common.storage.config.ConfigBuildCtx;
import me.cortex.voxy.common.storage.config.StorageConfig;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.lmdb.MDBVal;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static org.lwjgl.util.lmdb.LMDB.*;
import static org.lwjgl.util.lmdb.LMDB.MDB_NOTFOUND;

public class LMDBStorageBackend extends StorageBackend {
    private static final long GROW_SIZE = 1<<25;//Grow by 33 mb each time

    private final AtomicInteger accessingCounts = new AtomicInteger();
    private final ReentrantLock resizeLock = new ReentrantLock();

    private final LMDBInterface dbi;
    private final LMDBInterface.Database sectionDatabase;
    private final LMDBInterface.Database idMappingDatabase;
    public LMDBStorageBackend(String file) {
        this.dbi = new LMDBInterface.Builder()
                .setMaxDbs(2)
                .open(file, MDB_NOSUBDIR)//MDB_NOLOCK (IF I DO THIS, must sync the db manually)// TODO: THIS
                .fetch();
        this.dbi.setMapSize(GROW_SIZE);
        this.sectionDatabase = this.dbi.createDb("world_sections");
        this.idMappingDatabase = this.dbi.createDb("id_mapping");
    }

    private void growEnv() {
        long size = this.dbi.getMapSize() + GROW_SIZE;
        System.out.println("Growing DBI env size to: " + size + " bytes");
        this.dbi.setMapSize(size);
    }

    //TODO: try optimize this hellscape of spagetti locking
    private <T> T resizingTransaction(Supplier<T> transaction) {
        while (true) {
            try {
                return this.synchronizedTransaction(transaction);
            } catch (Throwable e) {
                if (e.getMessage().startsWith("Code: -30792")) {
                    if (this.resizeLock.tryLock()) {
                        //We must wait until all the other transactions have finished before we can resize
                        while (this.accessingCounts.get() != 0) {
                            Thread.onSpinWait();
                        }

                        this.growEnv();

                        this.resizeLock.unlock();
                    }
                } else {
                    throw e;
                }
            }
        }
    }

    private <T> T synchronizedTransaction(Supplier<T> transaction) {
        try {
            this.accessingCounts.getAndAdd(1);
            //Check if its locked, if it is locked then need to release the access, wait till resize is finished then
            while (this.resizeLock.isLocked()) {
                this.accessingCounts.getAndAdd(-1);
                while (this.resizeLock.isLocked()) {
                    Thread.onSpinWait();
                }
                this.accessingCounts.getAndAdd(1);
            }
            return transaction.get();
        } finally {
            this.accessingCounts.getAndAdd(-1);
        }
    }

    //TODO: make batch get and updates
    public ByteBuffer getSectionData(long key) {
        return this.synchronizedTransaction(() -> this.sectionDatabase.transaction(MDB_RDONLY, transaction->{
            var buff = transaction.stack.malloc(8);
            buff.putLong(0, key);
            var bb = transaction.get(buff);
            if (bb == null) {
                return null;
            }
            var copy = MemoryUtil.memAlloc(bb.remaining());
            MemoryUtil.memCopy(bb, copy);
            return copy;
        }));
    }

    //TODO: pad data to like some alignemnt so that when the section gets saved or updated
    // it can use the same allocation
    public void setSectionData(long key, ByteBuffer data) {
        this.resizingTransaction(() -> this.sectionDatabase.transaction(transaction->{
            var keyBuff = transaction.stack.malloc(8);
            keyBuff.putLong(0, key);
            transaction.put(keyBuff, data, 0);
            return null;
        }));
    }

    public void deleteSectionData(long key) {
        this.synchronizedTransaction(() -> this.sectionDatabase.transaction(transaction->{
            var keyBuff = transaction.stack.malloc(8);
            keyBuff.putLong(0, key);
            transaction.del(keyBuff);
            return null;
        }));
    }

    public synchronized void putIdMapping(int id, ByteBuffer data) {
        this.resizingTransaction(()->this.idMappingDatabase.transaction(transaction->{
            var keyBuff = transaction.stack.malloc(4);
            keyBuff.putInt(0, id);
            transaction.put(keyBuff, data, 0);
            return null;
        }));
    }

    public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
        return this.synchronizedTransaction(() -> {
            Int2ObjectOpenHashMap<byte[]> mapping = new Int2ObjectOpenHashMap<>();
            this.idMappingDatabase.transaction(MDB_RDONLY, transaction -> {
                try (var cursor = transaction.createCursor()) {
                    var keyPtr = MDBVal.malloc(transaction.stack);
                    var valPtr = MDBVal.malloc(transaction.stack);
                    while (cursor.get(MDB_NEXT, keyPtr, valPtr) != MDB_NOTFOUND) {
                        int keyVal = keyPtr.mv_data().getInt(0);
                        byte[] data = new byte[(int) valPtr.mv_size()];
                        Objects.requireNonNull(valPtr.mv_data()).get(data);
                        if (mapping.put(keyVal, data) != null) {
                            throw new IllegalStateException("Multiple mappings to same id");
                        }
                    }
                }
                return null;
            });
            return mapping;
        });
    }

    public void flush() {
        this.dbi.flush(true);
    }

    public void close() {
        this.sectionDatabase.close();
        this.idMappingDatabase.close();
        this.dbi.close();
    }

    public static class Config extends StorageConfig {
        @Override
        public StorageBackend build(ConfigBuildCtx ctx) {
            return new LMDBStorageBackend(ctx.ensurePathExists(ctx.substituteString(ctx.resolvePath())));
        }

        public static String getConfigTypeName() {
            return "LMDB";
        }
    }
}
