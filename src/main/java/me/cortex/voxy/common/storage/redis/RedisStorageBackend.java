package me.cortex.voxy.common.storage.redis;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.storage.StorageBackend;
import org.lwjgl.system.MemoryUtil;
import redis.clients.jedis.JedisPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class RedisStorageBackend extends StorageBackend {
    private final JedisPool pool = new JedisPool("localhost", 6379);
    private final byte[] WORLD = "world_sections".getBytes(StandardCharsets.UTF_8);
    private final byte[] MAPPINGS = "id_mappings".getBytes(StandardCharsets.UTF_8);

    public RedisStorageBackend() {

    }

    @Override
    public ByteBuffer getSectionData(long key) {
        try (var jedis = this.pool.getResource()) {
            var result = jedis.hget(WORLD, longToBytes(key));
            if (result == null) {
                return null;
            }
            //Need to copy to native memory
            var buffer = MemoryUtil.memAlloc(result.length);
            buffer.put(result);
            buffer.rewind();
            return buffer;
        }
    }

    @Override
    public void setSectionData(long key, ByteBuffer data) {
        try (var jedis = this.pool.getResource()) {
            var buffer = new byte[data.remaining()];
            data.get(buffer);
            data.rewind();
            jedis.hset(WORLD, longToBytes(key), buffer);
        }
    }

    @Override
    public void deleteSectionData(long key) {
        try (var jedis = this.pool.getResource()) {
            jedis.hdel(WORLD, longToBytes(key));
        }
    }

    @Override
    public void putIdMapping(int id, ByteBuffer data) {
        try (var jedis = this.pool.getResource()) {
            var buffer = new byte[data.remaining()];
            data.get(buffer);
            data.rewind();
            jedis.hset(MAPPINGS, intToBytes(id), buffer);
        }
    }

    @Override
    public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
        try (var jedis = this.pool.getResource()) {
            var mappings = jedis.hgetAll(MAPPINGS);
            var out = new Int2ObjectOpenHashMap<byte[]>();
            if (mappings == null) {
                return out;
            }
            for (var entry : mappings.entrySet()) {
                out.put(bytesToInt(entry.getKey()), entry.getValue());
            }
            return out;
        }
    }

    @Override
    public void flush() {

    }

    @Override
    public void close() {
        this.pool.close();
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
}
