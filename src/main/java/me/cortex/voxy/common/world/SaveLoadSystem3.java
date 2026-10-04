package me.cortex.voxy.common.world;

import it.unimi.dsi.fastutil.longs.Long2ShortOpenHashMap;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.util.ThreadLocalMemoryBuffer;
import me.cortex.voxy.common.world.other.Mapper;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.util.Objects;

public class SaveLoadSystem3 {
    public static final int STORAGE_VERSION = 0;

    private static final class SerializationCache {
        public final Long2ShortOpenHashMap lutMapCache = new Long2ShortOpenHashMap(1024);
        public final MemoryBuffer memoryBuffer = ThreadLocalMemoryBuffer.create(WorldSection.SECTION_VOLUME * 2 + WorldSection.SECTION_VOLUME * 8 + 1024);
        public short[] remappingArrayCache;
        public SerializationCache() {
            this.lutMapCache.defaultReturnValue((short) -1);
        }
    }
    public static int lin2z(int i) {//y,z,x
        int x = i&0x1F;
        int y = (i>>10)&0x1F;
        int z = (i>>5)&0x1F;
        return Integer.expand(x,0b1001001001001)|Integer.expand(y,0b10010010010010)|Integer.expand(z,0b100100100100100);

        //zyxzyxzyxzyxzyx
    }

    public static int z2lin(int i) {
        int x = Integer.compress(i, 0b1001001001001);
        int y = Integer.compress(i, 0b10010010010010);
        int z = Integer.compress(i, 0b100100100100100);
        return x|(y<<10)|(z<<5);
    }

    private static final ThreadLocal<SerializationCache> CACHE = ThreadLocal.withInitial(SerializationCache::new);

    //TODO: Cache like long2short and the short and other data to stop allocs
    public static MemoryBuffer serialize(WorldSection section) {
        var cache = CACHE.get();
        var data = section.data;

        Long2ShortOpenHashMap LUT = cache.lutMapCache; LUT.clear();

        MemoryBuffer buffer = cache.memoryBuffer.createUntrackedUnfreeableReference();
        long ptr = buffer.address;

        MemoryUtil.memPutLong(ptr, section.key); ptr += 8;
        long metadataPtr = ptr; ptr += 8;

        long blockPtr = ptr; ptr += WorldSection.SECTION_VOLUME*2;
        long prev = data[0]; MemoryUtil.memPutLong(ptr, prev); ptr+=8; LUT.put(prev, (short) 0);
        short mapping = 0;
        for (long block : data) {
            if (prev != block) {
                prev = block;
                mapping = LUT.putIfAbsent(block, (short) LUT.size());
                if (mapping == -1) {
                    mapping = (short) (LUT.size()-1);
                    MemoryUtil.memPutLong(ptr, block); ptr+=8;
                }
            }
            MemoryUtil.memPutShort(blockPtr, mapping); blockPtr+=2;
        }
        if (LUT.size() >= 1<<16) {
            throw new IllegalStateException();
        }

        //TODO: note! can actually have the first (last?) byte of metadata be the storage version!
        long metadata = 0;
        metadata |= Integer.toUnsignedLong(LUT.size());//Bottom 2 bytes
        metadata |= Byte.toUnsignedLong(section.getNonEmptyChildren())<<16;//Next byte
        //5 bytes free

        MemoryUtil.memPutLong(metadataPtr, metadata);
        //TODO: do hash

        return buffer.subSize(ptr-buffer.address);//Does not get freed
    }

    public static boolean deserialize(WorldSection section, MemoryBuffer data) {
        long ptr = data.address;
        long key = MemoryUtil.memGetLong(ptr); ptr += 8;

        if (section.key != key) {
            //throw new IllegalStateException("Decompressed section not the same as requested. got: " + key + " expected: " + section.key);
            Logger.error("Decompressed section not the same as requested. got: " + key + " expected: " + section.key);
            return false;
        }

        final long metadata = MemoryUtil.memGetLong(ptr); ptr += 8;
        section.nonEmptyChildren = (byte) ((metadata>>>16)&0xFF);
        final long lutBasePtr = ptr + WorldSection.SECTION_VOLUME * 2;

        final var blockData = section.data;
        for (int i = 0; i < WorldSection.SECTION_VOLUME; i++) {
            blockData[i] = MemoryUtil.memGetLong(lutBasePtr + Short.toUnsignedLong(MemoryUtil.memGetShort(ptr)) * 8L);ptr += 2;
        }

        if (section.lvl == 0) {
            int notEmpty = 0;
            for (long block : blockData) {
                notEmpty += Mapper.isNotAirInt(block);
            }
            section.nonEmptyBlockCount = notEmpty;
        }

        ptr = lutBasePtr + (metadata & 0xFFFF) * 8L;
        return true;
    }


    //Warning: mutates the inout buffer directly, returns a sliced version of the input if something changed (frees the memory)
    public static MemoryBuffer remap(MemoryBuffer inout, int[] blockStates, int@Nullable [] biomes) {
        if (blockStates == null) throw new IllegalArgumentException();
        long ptr = inout.address;
        long key = MemoryUtil.memGetLong(ptr); ptr += 8;
        //dont care about the key


        long metadata = MemoryUtil.memGetLong(ptr); final long metadataPtr = ptr; ptr += 8;
        final int lutSize  = (int) (metadata&0xFFFF);
        final long lutBasePtr = ptr + WorldSection.SECTION_VOLUME * 2;
        long lutWritePtr = lutBasePtr;

        var cache = CACHE.get();
        Long2ShortOpenHashMap LUT = cache.lutMapCache; LUT.clear();
        short[] lutRemap = cache.remappingArrayCache;
        if (lutRemap == null || lutRemap.length < lutSize) {
            lutRemap = cache.remappingArrayCache = new short[lutSize];
        }

        boolean changed = false;
        //For perf reasons, split it into 2 different varients
        if (biomes == null) {
            for (int i = 0; i < lutSize; i++) {
                long value = MemoryUtil.memGetLong(lutBasePtr+8L*i);
                long rvalue = Mapper.withBlock(value, blockStates[Mapper.getBlockId(value)]);
                changed |= rvalue != value; value = rvalue;
                short remapped = LUT.putIfAbsent(value, (short) LUT.size());
                if (remapped == -1) {
                    remapped = (short) (LUT.size()-1);
                    MemoryUtil.memPutLong(lutWritePtr, value); lutWritePtr+=8;
                }
                lutRemap[i] = remapped;
            }
        } else {
            for (int i = 0; i < lutSize; i++) {
                long value = MemoryUtil.memGetLong(lutBasePtr+8L*i);
                long rvalue = Mapper.withBlockBiome(value, blockStates[Mapper.getBlockId(value)], biomes[Mapper.getBiomeId(value)]);
                changed |= rvalue != value; value = rvalue;
                short remapped = LUT.putIfAbsent(value, (short) LUT.size());
                if (remapped == -1) {
                    remapped = (short) (LUT.size()-1);
                    MemoryUtil.memPutLong(lutWritePtr, value); lutWritePtr+=8;
                }
                lutRemap[i] = remapped;
            }
        }
        //if nothing changed then just return
        if (!changed) return null;//Nothing changed

        //Stuff changed, remap the contents
        for (int i = 0; i < WorldSection.SECTION_VOLUME; i++) {
            MemoryUtil.memPutShort(ptr, lutRemap[MemoryUtil.memGetShort(ptr)]);ptr += 2;
        }

        //Update the metadata for number of lut entries
        metadata &= ~0xFFFF;
        metadata |= LUT.size();
        MemoryUtil.memPutLong(metadataPtr, metadata);

        ptr = lutWritePtr;


        return inout.subSize(ptr - inout.address);
    }
}
