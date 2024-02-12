package me.cortex.voxy.common.voxelization;


import me.cortex.voxy.common.world.other.Mapper;

//16x16x16 block section
public class VoxelizedSection {
    public final int x;
    public final int y;
    public final int z;
    final long[] section;
    public VoxelizedSection(long[] section, int x, int y, int z) {
        this.section = section;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    private static int getIdx(int x, int y, int z, int shiftBy, int size) {
        int M = (1<<size)-1;
        x = (x>>shiftBy)&M;
        y = (y>>shiftBy)&M;
        z = (z>>shiftBy)&M;
        return (y<<(size<<1))|(z<<size)|(x);
    }

    public long get(int lvl, int x, int y, int z) {
        int offset = lvl==1?(1<<12):0;
        offset |= lvl==2?(1<<12)|(1<<9):0;
        offset |= lvl==3?(1<<12)|(1<<9)|(1<<6):0;
        offset |= lvl==4?(1<<12)|(1<<9)|(1<<6)|(1<<3):0;
        return this.section[getIdx(x, y, z, 0, 4-lvl) + offset];
    }

    public static VoxelizedSection createEmpty(int x, int y, int z) {
        return new VoxelizedSection(new long[16*16*16 + 8*8*8 + 4*4*4 + 2*2*2 + 1], x, y, z);
    }
}
