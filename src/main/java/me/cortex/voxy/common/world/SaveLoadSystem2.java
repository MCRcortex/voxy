package me.cortex.voxy.common.world;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.util.UnsafeUtil;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;

public class SaveLoadSystem2 {

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


    private record SerialCache() {}
    private record DeserialCache() {}
    private static final ThreadLocal<SerialCache> SERIALIZE_CACHE = ThreadLocal.withInitial(()->new SerialCache());
    private static final ThreadLocal<DeserialCache> DESERIALIZE_CACHE = ThreadLocal.withInitial(()->new DeserialCache());


    //TODO: make it so that MemoryBuffer is cached and reused
    public static MemoryBuffer serialize(WorldSection section) {
        var cache = SERIALIZE_CACHE.get();
        //Split into separate block, biome, blocklight, skylight
        // where block and biome are pelleted (0 block id (air) is implicitly in the pallet )
        // if all entries in a specific array are the same, just emit that single value
        // do bitpacking on the resulting arrays for pallets/when packing the palleted arrays
        // if doing bitpacking + pallet is larger than just emitting raw entries, do that

        //Header includes position (long), (maybe time?), version storage type/version, child existence, air block count?
        long[] data = new long[WorldSection.SECTION_VOLUME];
        section.copyDataTo(data);

        boolean allSameBlockLight = true;
        boolean allSameSkyLight = true;
        byte[] blockLight = new byte[32*32*32/2];
        byte[] skyLight = new byte[32*32*32/2];
        short[] blocks = new short[32*32*32];
        short[] biome = new short[32*32*32];

        Int2IntOpenHashMap blockMapping = new Int2IntOpenHashMap();blockMapping.defaultReturnValue(-1);
        Int2IntOpenHashMap biomeMapping = new Int2IntOpenHashMap();biomeMapping.defaultReturnValue(-1);
        int[] blockLutVals = new int[32*32*32];
        int[] biomeLutVals = new int[32*32*32];

        long hash = 12345;
        for (int i = 0; i < WorldSection.SECTION_VOLUME; i++) {
            long state = data[lin2z(i)];//Sample from Z curve

            hash ^= state*1892671+19827911;
            hash *= 198729111; hash ^= hash >>> 32;

            byte bl = (byte) ((Mapper.getLightId(state)>>4)&0xF);
            blockLight[i>>1] |= (byte) (bl<<((i&1)*4));
            byte sl = (byte) (Mapper.getLightId(state)&0xF);
            skyLight[i>>1] |= (byte) (sl<<((i&1)*4));
            if (i!=0) {
                allSameBlockLight &= (blockLight[0]&0xF) == bl;
                allSameSkyLight &= (skyLight[0]&0xF) == sl;
            }
            {
                int bid = blockMapping.putIfAbsent(Mapper.getBlockId(state), blockMapping.size());
                if (bid == -1) {
                    bid = blockMapping.size() - 1;
                    blockLutVals[bid] = Mapper.getBlockId(state);
                }
                blocks[i] = (short) bid;
            }
            {
                int bid = biomeMapping.putIfAbsent(Mapper.getBiomeId(state), biomeMapping.size());
                if (bid == -1) {
                    bid = biomeMapping.size() - 1;
                    biomeLutVals[bid] = Mapper.getBiomeId(state);
                }
                biome[i] = (short) bid;
            }
        }



        var res = new MemoryBuffer(32*32*32*8+1024);
        long ptr = res.address;
        MemoryUtil.memPutLong(ptr, section.key); ptr += 8;
        MemoryUtil.memPutLong(ptr, hash); ptr += 8;

        int meta = 0;
        meta |= allSameBlockLight?1:0;
        meta |= allSameSkyLight?2:0;
        meta |= (biomeMapping.size()-1)<<2;//512 max size
        meta |= (blockMapping.size()-1)<<11;//4096 max size
        meta |= Byte.toUnsignedInt(section.nonEmptyChildren) << 23;
        MemoryUtil.memPutInt(ptr, meta); ptr += 4;

        //Encode lighting
        /*
        //Micro storage optimization, not done cause makes decode very slightly slower and more pain
        if (allSameBlockLight && allSameSkyLight) {
            MemoryUtil.memPutByte(ptr, (byte) ((blockLight[0]&0xF)|((skyLight[0]&0xF)<<4))); ptr += 1;
        } else*/
        {
            if (allSameBlockLight) {
                MemoryUtil.memPutByte(ptr, (byte) (blockLight[0] & 0xF)); ptr += 1;
            } else {
                UnsafeUtil.memcpy(blockLight, ptr); ptr += blockLight.length;
            }
            if (allSameSkyLight) {
                MemoryUtil.memPutByte(ptr, (byte) (skyLight[0] & 0xF)); ptr += 1;
            } else {
                UnsafeUtil.memcpy(skyLight, ptr); ptr += skyLight.length;
            }
        }

        //Compact encoding of block and biome mappinigs
        {
            int rem = 32;
            int batch = 0;
            {//Block
                int SIZE = 20;// 20 bits per entry
                for (int i = 0; i < blockMapping.size(); i++) {
                    int b = blockLutVals[i];
                    if (rem != 0)
                        batch |= (b << (32 - rem));//the shift does auto cutoff
                    if (rem < SIZE) {
                        MemoryUtil.memPutInt(ptr, batch);
                        ptr += 4;
                        batch = b >> rem;
                        rem = 32 - (SIZE - rem);
                    } else {
                        rem -= SIZE;
                    }
                }
            }
            {//Biome
                int SIZE = 9;//9 bits per entry
                for (int i = 0; i < biomeMapping.size(); i++) {
                    int b = biomeLutVals[i];
                    if (rem != 0)
                        batch |= (b << (32 - rem));//the shift does auto cutoff
                    if (rem < SIZE) {
                        MemoryUtil.memPutInt(ptr, batch); ptr += 4;
                        batch = b >> rem;
                        rem = 32 - (SIZE - rem);
                    } else {
                        rem -= SIZE;
                    }
                }
            }
            if (rem != 32) {
                MemoryUtil.memPutInt(ptr, batch); ptr += 4;
            }
        }

        //TODO: see if tight bitpacking is better or if bitpacking with pow2 pack size is better

        {//Store blocks
            final int SIZE = MathHelper.smallestEncompassingPowerOfTwo(MathHelper.floorLog2(MathHelper.smallestEncompassingPowerOfTwo(blockMapping.size())));

            int rem = 32;
            int batch = 0;

            for (int b : blocks) {
                if (rem != 0)
                    batch |= (b << (32 - rem));//the shift does auto cutoff
                if (rem < SIZE) {
                    MemoryUtil.memPutInt(ptr, batch);
                    ptr += 4;
                    batch = b >> rem;
                    rem = 32 - (SIZE - rem);
                } else {
                    rem -= SIZE;
                }
            }
            if (rem != 32) {
                MemoryUtil.memPutInt(ptr, batch); ptr += 4;
            }
        }
        {//Store biome
            if (biomeMapping.size() == 1) {
                //If its only a single mapping, dont put anything
            } else {
                final int SIZE = MathHelper.smallestEncompassingPowerOfTwo(MathHelper.floorLog2(MathHelper.smallestEncompassingPowerOfTwo(biomeMapping.size())));

                int rem = 32;
                int batch = 0;

                for (int b : biome) {
                    if (rem != 0)
                        batch |= (b << (32 - rem));//the shift does auto cutoff
                    if (rem < SIZE) {
                        MemoryUtil.memPutInt(ptr, batch);
                        ptr += 4;
                        batch = b >> rem;
                        rem = 32 - (SIZE - rem);
                    } else {
                        rem -= SIZE;
                    }
                }
                if (rem != 32) {
                    MemoryUtil.memPutInt(ptr, batch); ptr += 4;
                }
            }
        }
        return res.subSize(ptr - res.address);

    }

    public static boolean deserialize(WorldSection section, MemoryBuffer data) {
        var cache = DESERIALIZE_CACHE.get();

        long ptr = data.address;
        long pos = MemoryUtil.memGetLong(ptr); ptr += 8;
        if (section.key != pos) {
            Logger.error("Section pos not the same as requested, got " + pos + " expected " + section.key);
            return false;
        }
        long chash = MemoryUtil.memGetLong(ptr); ptr += 8;
        int meta = MemoryUtil.memGetInt(ptr); ptr += 4;

        boolean allSameBlockLight = (meta&1)!=0;
        boolean allSameSkyLight = (meta&2)!=0;
        int biomeMapSize = ((meta>>2)&0x1FF)+1;
        int blockMapSize = ((meta>>11)&((1<<12)-1))+1;
        section._unsafeSetNonEmptyChildren((byte) ((meta>>23)&0xFF));

        long blockLight;
        long skyLight;

        if (allSameBlockLight) {
            //shift up 4 so that its already in correct position
            blockLight = Byte.toUnsignedLong(MemoryUtil.memGetByte(ptr))<<4; ptr += 1;
        } else {
            blockLight = ptr; ptr += 32*32*32/2;
        }
        if (allSameSkyLight) {
            skyLight = MemoryUtil.memGetByte(ptr); ptr += 1;
        } else {
            skyLight = ptr; ptr += 32*32*32/2;
        }

        int[] blockLut = new int[blockMapSize];
        int[] biomeLut = new int[biomeMapSize];
        {//Deserialize the block and biome mappings
            int rem = 32;
            int batch = MemoryUtil.memGetInt(ptr); ptr += 4;
            {//Block
                int SIZE = 20;// 20 bits per entry
                int msk = (1<<SIZE)-1;
                for (int i = 0; i < blockMapSize; i++) {
                    int val = batch&msk;
                    batch >>>= SIZE; rem -= SIZE;
                    if (rem < 0) {
                        batch = MemoryUtil.memGetInt(ptr); ptr += 4;
                        val |= (batch&((1<<-rem)-1))<<(SIZE+rem);
                        batch >>>= -rem;
                        rem = 32+rem;
                    }
                    blockLut[i] = val;
                }
            }
            {//Biome
                int SIZE = 9;// 9 bits per entry
                int msk = (1<<SIZE)-1;
                for (int i = 0; i < biomeMapSize; i++) {
                    int val = batch&msk;
                    batch >>>= SIZE; rem -= SIZE;
                    if (rem < 0) {
                        batch = MemoryUtil.memGetInt(ptr); ptr += 4;
                        val |= (batch&((1<<-rem)-1))<<(SIZE+rem);
                        batch >>>= -rem;
                        rem = 32+rem;
                    }
                    biomeLut[i] = val;
                }
            }
        }

        //unpack block and biome
        short[] blocks = new short[32*32*32];
        short[] biomes = new short[32*32*32];
        {//Block
            final int SIZE = MathHelper.smallestEncompassingPowerOfTwo(MathHelper.floorLog2(MathHelper.smallestEncompassingPowerOfTwo(blockMapSize)));
            int rem = 32;
            int batch = MemoryUtil.memGetInt(ptr); ptr += 4;
            int msk = (1<<SIZE)-1;
            for (int i = 0; i < blocks.length; i++) {
                int val = batch&msk;
                batch >>>= SIZE; rem -= SIZE;
                if (rem < 0) {
                    batch = MemoryUtil.memGetInt(ptr); ptr += 4;
                    val |= (batch&((1<<-rem)-1))<<(SIZE+rem);
                    batch >>>= -rem;
                    rem = 32+rem;
                }
                blocks[i] = (short) val;
            }
        }
        {//Biome
            if (biomeMapSize == 1) {
                Arrays.fill(biomes, (short) 0);
            } else {
                final int SIZE = MathHelper.smallestEncompassingPowerOfTwo(MathHelper.floorLog2(MathHelper.smallestEncompassingPowerOfTwo(biomeMapSize)));
                int rem = 32;
                int batch = MemoryUtil.memGetInt(ptr); ptr += 4;
                int msk = (1<<SIZE)-1;
                for (int i = 0; i < biomes.length; i++) {
                    int val = batch&msk;
                    batch >>>= SIZE; rem -= SIZE;
                    if (rem < 0) {
                        batch = MemoryUtil.memGetInt(ptr); ptr += 4;
                        val |= (batch&((1<<-rem)-1))<<(SIZE+rem);
                        batch >>>= -rem;
                        rem = 32+rem;
                    }
                    biomes[i] = (short) val;
                }
            }
        }

        //Reconstruct everything
        long hash = 12345;
        for (int i = 0; i < 32*32*32; i++) {
            byte light = 0;
            {
                if (allSameBlockLight) {
                    light |= (byte) (blockLight&0xF0);
                } else {
                    //Todo clean and optimize this (it can be optimized alot)
                    light |= (byte) (((Byte.toUnsignedInt(MemoryUtil.memGetByte(blockLight+(i>>1)))>>((i&1)*4))&0xF)<<4);
                }
                if (allSameSkyLight) {
                    light |= (byte) (skyLight&0xF);
                } else {
                    //Todo clean and optimize this (it can be optimized alot)
                    light |= (byte) ((Byte.toUnsignedInt(MemoryUtil.memGetByte(skyLight+(i>>1)))>>((i&1)*4))&0xF);
                }
            }

            int block = blockLut[blocks[i]];
            int biome = biomeLut[biomes[i]];

            long state = Mapper.composeMappingId(light, block, biome);

            hash ^= state*1892671+19827911;
            hash *= 198729111; hash ^= hash >>> 32;
            section.data[lin2z(i)] = state;
        }
        if (chash != hash) {
            Logger.error("Hash does not match what is expected, got: " + hash + " expected: " + chash);
            return false;
        }

        return true;
    }


    public static void main2(String[] args) {
        var aa = new MemoryBuffer(502400);
        int blockMapSize = 100000;
        {
            long ptr = aa.address;

            int rem = 32;
            int batch = 0;
            {//Block
                int SIZE = 20;// 20 bits per entry
                for (int i = 0; i < blockMapSize; i++) {
                    int b = i;

                    if (rem != 0)
                        batch |= (b << (32 - rem));//the shift does auto cutoff
                    if (rem < SIZE) {
                        MemoryUtil.memPutInt(ptr, batch);
                        ptr += 4;
                        batch = b >> rem;
                        rem = 32 - (SIZE - rem);
                    } else {
                        rem -= SIZE;
                    }
                }
            }
            if (rem != 32) {
                MemoryUtil.memPutInt(ptr, batch); ptr += 4;
            }
            System.err.println(ptr-aa.address);
        }


        {
            long ptr = aa.address;
            int rem = 32;
            int batch = MemoryUtil.memGetInt(ptr); ptr += 4;
            {//Block
                int SIZE = 20;// 20 bits per entry
                int msk = (1<<SIZE)-1;
                for (int i = 0; i < blockMapSize; i++) {
                    int val = batch&msk;
                    batch >>>= SIZE; rem -= SIZE;
                    if (rem < 0) {
                        batch = MemoryUtil.memGetInt(ptr); ptr += 4;
                        val |= (batch&((1<<-rem)-1))<<(SIZE+rem);
                        batch >>>= -rem;
                        rem = 32+rem;
                    }
                    //System.out.println(val);
                    if (val != i) {
                        throw new IllegalStateException();
                    }
                }
            }
            System.err.println(ptr-aa.address);
        }

    }


    public static void main(String[] args) {
        var test = WorldSection._createRawUntrackedUnsafeSection(0,1,2,3);
        test._unsafeSetNonEmptyChildren((byte) 0b10110011);
        for (int i = 0; i < 32*32*32; i++) {
            test.data[i] = Mapper.composeMappingId((byte) (i%256), 12+(i%1666), i%300);
        }

        var res = serialize(test);

        var test2 = WorldSection._createRawUntrackedUnsafeSection(test.lvl, test.x, test.y, test.z);
        System.out.println(deserialize(test2, res));
        int a = 0;
        for (int i = 0; i < 32*32*32; i++) {
            if (test.data[i] != test2.data[i]) {
                throw new IllegalStateException();
            }
        }

    }
}
