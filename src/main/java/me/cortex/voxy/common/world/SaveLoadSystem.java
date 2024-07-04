package me.cortex.voxy.common.world;

import it.unimi.dsi.fastutil.longs.Long2ShortOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.util.zstd.Zstd.*;

public class SaveLoadSystem {

    //TODO: Cache like long2short and the short and other data to stop allocs
    public static ByteBuffer serialize(WorldSection section) {
        var data = section.copyData();
        var compressed = new short[data.length];
        Long2ShortOpenHashMap LUT = new Long2ShortOpenHashMap(data.length);
        LongArrayList LUTVAL = new LongArrayList();
        for (int i = 0; i < data.length; i++) {
            long block = data[i];
            short mapping = LUT.computeIfAbsent(block, id->{
                LUTVAL.add(id);
                return (short)(LUTVAL.size()-1);
            });
            compressed[i] = mapping;
        }
        long[] lut = LUTVAL.toLongArray();
        ByteBuffer raw = MemoryUtil.memAlloc(compressed.length*2+lut.length*8+512);

        long hash = section.key^(lut.length*1293481298141L);
        raw.putLong(section.key);
        raw.putInt(lut.length);
        for (long id : lut) {
            raw.putLong(id);
            hash *= 1230987149811L;
            hash += 12831;
            hash ^= id;
        }

        for (int i = 0; i < compressed.length; i++) {
            short block = compressed[i];
            raw.putShort(block);
            hash *= 1230987149811L;
            hash += 12831;
            hash ^= (block*1827631L) ^ data[i];
        }

        raw.putLong(hash);

        raw.limit(raw.position());
        raw.rewind();

        return raw;
    }

    public static boolean deserialize(WorldSection section, ByteBuffer data, boolean ignoreMismatchPosition) {
        long hash = 0;
        long key = data.getLong();
        int lutLen = data.getInt();
        long[] lut = new long[lutLen];
        hash = key^(lut.length*1293481298141L);
        for (int i = 0; i < lutLen; i++) {
            lut[i] = data.getLong();
            hash *= 1230987149811L;
            hash += 12831;
            hash ^= lut[i];
        }

        if ((!ignoreMismatchPosition) && section.key != key) {
            //throw new IllegalStateException("Decompressed section not the same as requested. got: " + key + " expected: " + section.key);
            System.err.println("Decompressed section not the same as requested. got: " + key + " expected: " + section.key);
            return false;
        }

        for (int i = 0; i < section.data.length; i++) {
            short lutId = data.getShort();
            section.data[i] = lut[lutId];
            hash *= 1230987149811L;
            hash += 12831;
            hash ^= (lutId*1827631L) ^ section.data[i];
        }

        long expectedHash = data.getLong();
        if (expectedHash != hash) {
            //throw new IllegalStateException("Hash mismatch got: " + hash + " expected: " + expectedHash);
            System.err.println("Hash mismatch got: " + hash + " expected: " + expectedHash + " removing region");
            return false;
        }

        if (data.hasRemaining()) {
            //throw new IllegalStateException("Decompressed section had excess data");
            System.err.println("Decompressed section had excess data removing region");
            return false;
        }
        return true;
    }
}
