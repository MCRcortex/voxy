package me.cortex.voxy.common.storage.rocksdb;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.storage.StorageBackend;
import me.cortex.voxy.common.storage.config.ConfigBuildCtx;
import me.cortex.voxy.common.storage.config.StorageConfig;
import org.lwjgl.system.MemoryUtil;
import org.rocksdb.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RocksDBStorageBackend extends StorageBackend {
    private final RocksDB db;
    private final ColumnFamilyHandle worldSections;
    private final ColumnFamilyHandle idMappings;

    //NOTE: closes in order
    private final List<AbstractImmutableNativeReference> closeList = new ArrayList<>();

    public RocksDBStorageBackend(String path) {
        var lockPath = new File(path).toPath().resolve("LOCK");
        if (Files.exists(lockPath)) {
            System.err.println("WARNING, deleting rocksdb LOCK file");
            int attempts = 10;
            while (attempts-- != 0) {
                try {
                    Files.delete(lockPath);
                    break;
                } catch (IOException e) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
            if (Files.exists(lockPath)) {
                throw new RuntimeException("Unable to delete rocksdb lock file");
            }
        }

        final ColumnFamilyOptions cfOpts = new ColumnFamilyOptions().optimizeUniversalStyleCompaction();

        final List<ColumnFamilyDescriptor> cfDescriptors = Arrays.asList(
            new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, cfOpts),
            new ColumnFamilyDescriptor("world_sections".getBytes(), cfOpts),
            new ColumnFamilyDescriptor("id_mappings".getBytes(), cfOpts)
        );

        final DBOptions options = new DBOptions()
                .setCreateIfMissing(true)
                .setCreateMissingColumnFamilies(true);

        List<ColumnFamilyHandle> handles = new ArrayList<>();

        try {
            this.db = RocksDB.open(options,
                    path, cfDescriptors,
                    handles);

            this.closeList.addAll(handles);
            this.closeList.add(this.db);
            this.closeList.add(options);
            this.closeList.add(cfOpts);

            this.worldSections = handles.get(1);
            this.idMappings = handles.get(2);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ByteBuffer getSectionData(long key) {
        try {
            var result = this.db.get(this.worldSections, longToBytes(key));
            if (result == null) {
                return null;
            }
            //Need to copy to native memory
            var buffer = MemoryUtil.memAlloc(result.length);
            buffer.put(result);
            buffer.rewind();
            return buffer;
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setSectionData(long key, ByteBuffer data) {
        try {
            var buffer = new byte[data.remaining()];
            data.get(buffer);
            data.rewind();
            this.db.put(this.worldSections, longToBytes(key), buffer);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteSectionData(long key) {
        try {
            this.db.delete(this.worldSections, longToBytes(key));
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void putIdMapping(int id, ByteBuffer data) {
        try {
            var buffer = new byte[data.remaining()];
            data.get(buffer);
            data.rewind();
            this.db.put(this.idMappings, intToBytes(id), buffer);
        } catch (
                RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
        var iterator = this.db.newIterator(this.idMappings);
        var out = new Int2ObjectOpenHashMap<byte[]>();
        for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
            out.put(bytesToInt(iterator.key()), iterator.value());
        }
        return out;
    }

    @Override
    public void flush() {
        try {
            this.db.flushWal(true);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        this.closeList.forEach(AbstractImmutableNativeReference::close);
    }

    private static byte[] intToBytes(int i) {
        return new byte[] {(byte)(i>>24), (byte)(i>>16), (byte)(i>>8), (byte) i};
    }
    private static int bytesToInt(byte[] i) {
        return (Byte.toUnsignedInt(i[0])<<24)|(Byte.toUnsignedInt(i[1])<<16)|(Byte.toUnsignedInt(i[2])<<8)|(Byte.toUnsignedInt(i[3]));
    }

    private static byte[] longToBytes(long l) {
        byte[] result = new byte[Long.BYTES];
        for (int i = Long.BYTES - 1; i >= 0; i--) {
            result[i] = (byte)(l & 0xFF);
            l >>= Byte.SIZE;
        }
        return result;
    }

    private static long bytesToLong(final byte[] b) {
        long result = 0;
        for (int i = 0; i < Long.BYTES; i++) {
            result <<= Byte.SIZE;
            result |= (b[i] & 0xFF);
        }
        return result;
    }

    public static class Config extends StorageConfig {
        @Override
        public StorageBackend build(ConfigBuildCtx ctx) {
            return new RocksDBStorageBackend(ctx.ensurePathExists(ctx.substituteString(ctx.resolvePath())));
        }

        public static String getConfigTypeName() {
            return "RocksDB";
        }
    }
}
