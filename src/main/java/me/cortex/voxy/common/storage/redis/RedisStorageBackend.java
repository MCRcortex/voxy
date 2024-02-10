package me.cortex.voxy.common.storage.redis;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.storage.StorageBackend;
import me.cortex.voxy.common.storage.config.ConfigBuildCtx;
import me.cortex.voxy.common.storage.config.StorageConfig;
import org.lwjgl.system.MemoryUtil;
import redis.clients.jedis.JedisPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class RedisStorageBackend extends StorageBackend {
    private final JedisPool pool;
    private final String user;
    private final String password;
    private final byte[] WORLD;
    private final byte[] MAPPINGS;

    public RedisStorageBackend(String host, int port, String prefix) {
        this(host, port, prefix, null, null);
    }

    public RedisStorageBackend(String host, int port, String prefix, String user, String password) {
        this.pool = new JedisPool(host, port);
        this.user = user;
        this.password = password;
        this.WORLD = (prefix+"world_sections").getBytes(StandardCharsets.UTF_8);
        this.MAPPINGS = (prefix+"id_mappings").getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public ByteBuffer getSectionData(long key) {
        try (var jedis = this.pool.getResource()) {
            if (this.user != null) {
                jedis.auth(this.user, this.password);
            }

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
            if (this.user != null) {
                jedis.auth(this.user, this.password);
            }

            var buffer = new byte[data.remaining()];
            data.get(buffer);
            data.rewind();
            jedis.hset(WORLD, longToBytes(key), buffer);
        }
    }

    @Override
    public void deleteSectionData(long key) {
        try (var jedis = this.pool.getResource()) {
            if (this.user != null) {
                jedis.auth(this.user, this.password);
            }

            jedis.hdel(WORLD, longToBytes(key));
        }
    }

    @Override
    public void putIdMapping(int id, ByteBuffer data) {
        try (var jedis = this.pool.getResource()) {
            if (this.user != null) {
                jedis.auth(this.user, this.password);
            }

            var buffer = new byte[data.remaining()];
            data.get(buffer);
            data.rewind();
            jedis.hset(MAPPINGS, intToBytes(id), buffer);
        }
    }

    @Override
    public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
        try (var jedis = this.pool.getResource()) {
            if (this.user != null) {
                jedis.auth(this.user, this.password);
            }

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

    public static class Config extends StorageConfig {
        public String host;
        public int port;
        public String prefix;

        @Override
        public StorageBackend build(ConfigBuildCtx ctx) {
            return new RedisStorageBackend(this.host, this.port, ctx.substituteString(this.prefix));
        }

        public static String getConfigTypeName() {
            return "Redis";
        }
    }
}
