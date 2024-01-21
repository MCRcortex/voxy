package me.cortex.zenith.common.world;

import it.unimi.dsi.fastutil.longs.Long2ShortOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.util.zstd.Zstd.*;

public class SaveLoadSystem {
    public static ByteBuffer serialize(WorldSection section, int compressionLevel) {
        var data = section.copyData();
        var compressed = new Short[data.length];
        Long2ShortOpenHashMap LUT = new Long2ShortOpenHashMap();
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

        long hash = section.getKey()^(lut.length*1293481298141L);
        raw.putLong(section.getKey());
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
        ByteBuffer compressedData  = MemoryUtil.memAlloc((int)ZSTD_COMPRESSBOUND(raw.remaining()));
        long compressedSize = ZSTD_compress(compressedData, raw, compressionLevel);
        compressedData.limit((int) compressedSize);
        compressedData.rewind();
        MemoryUtil.memFree(raw);

        //Compress into a key + data pallet format
        return compressedData;
    }

    public static boolean deserialize(WorldSection section, ByteBuffer data) {
        var decompressed = MemoryUtil.memAlloc(32*32*32*4*2);
        long size = ZSTD_decompress(decompressed, data);
        decompressed.limit((int) size);

        long hash = 0;
        long key = decompressed.getLong();
        int lutLen = decompressed.getInt();
        long[] lut = new long[lutLen];
        hash = key^(lut.length*1293481298141L);
        for (int i = 0; i < lutLen; i++) {
            lut[i] = decompressed.getLong();
            hash *= 1230987149811L;
            hash += 12831;
            hash ^= lut[i];
        }

        if (section.getKey() != key) {
            throw new IllegalStateException("Decompressed section not the same as requested. got: " + key + " expected: " + section.getKey());
        }

        for (int i = 0; i < section.data.length; i++) {
            short lutId = decompressed.getShort();
            section.data[i] = lut[lutId];
            hash *= 1230987149811L;
            hash += 12831;
            hash ^= (lutId*1827631L) ^ section.data[i];
        }

        long expectedHash = decompressed.getLong();
        if (expectedHash != hash) {
            //throw new IllegalStateException("Hash mismatch got: " + hash + " expected: " + expectedHash);
            System.err.println("Hash mismatch got: " + hash + " expected: " + expectedHash + " removing region");
            return false;
        }

        if (decompressed.hasRemaining()) {
            //throw new IllegalStateException("Decompressed section had excess data");
            System.err.println("Decompressed section had excess data removing region");
            return false;
        }
        MemoryUtil.memFree(decompressed);

        return true;
    }
}
