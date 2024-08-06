package me.cortex.voxy.common.world;

import it.unimi.dsi.fastutil.longs.Long2ShortOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.util.UnsafeUtil;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.util.zstd.Zstd.*;

public class SaveLoadSystem {
    public static final int BIGGEST_SERIALIZED_SECTION_SIZE = 32 * 32 * 32 * 8 * 2;

    public static int lin2z(int i) {
        int x = i&0x1F;
        int y = (i>>10)&0x1F;
        int z = (i>>5)&0x1F;
        return Integer.expand(x,0b1001001001001)|Integer.expand(y,0b10010010010010)|Integer.expand(z,0b100100100100100);
    }

    public static int z2lin(int i) {
        int x = Integer.compress(i, 0b1001001001001);
        int y = Integer.compress(i, 0b10010010010010);
        int z = Integer.compress(i, 0b100100100100100);
        return x|(y<<10)|(z<<5);
    }

    //TODO: Cache like long2short and the short and other data to stop allocs
    public static MemoryBuffer serialize(WorldSection section) {
        var data = section.copyData();
        var compressed = new short[data.length];
        Long2ShortOpenHashMap LUT = new Long2ShortOpenHashMap(data.length);
        LongArrayList LUTVAL = new LongArrayList();
        long pHash = 99;
        for (int i = 0; i < data.length; i++) {
            long block = data[i];
            short mapping = LUT.computeIfAbsent(block, id->{
                LUTVAL.add(id);
                return (short)(LUTVAL.size()-1);
            });
            compressed[lin2z(i)] = mapping;
            pHash *= 127817112311121L;
            pHash ^= pHash>>31;
            pHash += 9918322711L;
            pHash ^= block;
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
        hash ^= pHash;

        for (short block : compressed) {
            raw.putShort(block);
        }

        raw.putLong(hash);
        //The amount of memory copies are not ideal
        var out = new MemoryBuffer(raw.position());
        UnsafeUtil.memcpy(MemoryUtil.memAddress(raw), out.address, out.size);
        MemoryUtil.memFree(raw);
        return out;
    }

    public static boolean deserialize(WorldSection section, MemoryBuffer data, boolean ignoreMismatchPosition) {
        long ptr = data.address;
        long hash = 0;
        long key = MemoryUtil.memGetLong(ptr); ptr += 8;
        int lutLen = MemoryUtil.memGetInt(ptr); ptr += 4;
        long[] lut = new long[lutLen];
        hash = key^(lut.length*1293481298141L);
        for (int i = 0; i < lutLen; i++) {
            lut[i] = MemoryUtil.memGetLong(ptr); ptr += 8;
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
            section.data[z2lin(i)] = lut[MemoryUtil.memGetShort(ptr)]; ptr += 2;
        }

        long pHash = 99;
        for (long block : section.data) {
            pHash *= 127817112311121L;
            pHash ^= pHash>>31;
            pHash += 9918322711L;
            pHash ^= block;
        }
        hash ^= pHash;

        long expectedHash = MemoryUtil.memGetLong(ptr); ptr += 8;
        if (expectedHash != hash) {
            //throw new IllegalStateException("Hash mismatch got: " + hash + " expected: " + expectedHash);
            System.err.println("Hash mismatch got: " + hash + " expected: " + expectedHash + " removing region");
            return false;
        }

        return true;
    }
}
