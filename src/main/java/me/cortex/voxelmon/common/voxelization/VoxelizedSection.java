package me.cortex.voxelmon.common.voxelization;


import me.cortex.voxelmon.common.world.other.Mapper;

//16x16x16 block section
public class VoxelizedSection {
    public final int x;
    public final int y;
    public final int z;
    private final long[] section;
    private final long populationMsk;
    private final long[][] subSections;
    public VoxelizedSection(long[] section, long populationMsk, long[][] subSections, int x, int y, int z) {
        this.section = section;
        this.populationMsk = populationMsk;
        this.subSections = subSections;
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
        if (lvl < 2) {
            int subIdx = getIdx(x,y,z,2-lvl,2);
            var subSec = this.subSections[subIdx];
            if (subSec == null) {
                return Mapper.AIR;
            }

            if (lvl == 0) {
                return subSec[getIdx(x,y,z,0,2)];
            } else if (lvl == 1) {
                return subSec[4*4*4+getIdx(x,y,z,0,1)];
            }
        } else {
            if (lvl == 2) {
                return section[getIdx(x,y,z,0,2)];
            } else if (lvl == 3) {
                return section[4*4*4+getIdx(x,y,z,0,1)];
            } else if (lvl == 4) {
                return section[4*4*4+2*2*2];
            }
        }
        return Mapper.UNKNOWN_MAPPING;
    }

    public static VoxelizedSection createEmpty(int x, int y, int z) {
        return new VoxelizedSection(new long[4*4*4+2*2*2+1], 0, new long[4*4*4][], x, y, z);
    }
}
