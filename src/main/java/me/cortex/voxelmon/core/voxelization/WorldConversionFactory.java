package me.cortex.voxelmon.core.voxelization;

import me.cortex.voxelmon.core.world.other.Mipper;
import me.cortex.voxelmon.core.world.other.Mapper;
import net.minecraft.block.BlockState;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.ReadableContainer;

public class WorldConversionFactory {

    private static int I(int x, int y, int z) {
        return (y<<4)|(z<<2)|x;
    }

    private static int J(int x, int y, int z) {
        return ((y<<2)|(z<<1)|x) + 4*4*4;
    }

    //TODO: add a local mapper cache since it should be smaller and faster
    public static VoxelizedSection convert(Mapper stateMapper,
                                           PalettedContainer<BlockState> blockContainer,
                                           ReadableContainer<RegistryEntry<Biome>> biomeContainer,
                                           ILightingSupplier lightSupplier,
                                           int sx,
                                           int sy,
                                           int sz) {
        long[] section = new long[4*4*4+2*2*2+1];//Mipping
        long[][] subSections = new long[4*4*4][];
        long[] current = new long[4*4*4+2*2*2];
        long msk = 0;
        for (int oy = 0; oy < 4; oy++) {
            for (int oz = 0; oz < 4; oz++) {
                for (int ox = 0; ox < 4; ox++) {
                    RegistryEntry<Biome> biome = biomeContainer.get(ox, oy, oz);
                    int nonAir = 0;
                    for (int iy = 0; iy < 4; iy++) {
                        for (int iz = 0; iz < 4; iz++) {
                            for (int ix = 0; ix < 4; ix++) {
                                int x = (ox<<2)|ix;
                                int y = (oy<<2)|iy;
                                int z = (oz<<2)|iz;
                                var state = blockContainer.get(x, y, z);
                                byte light = lightSupplier.supply(x,y,z,state);
                                if (!(state.isAir() && (light==0))) {//TODO:FIXME:optimize this in such a way that having skylight access/no skylight means that an entire section is created, WHICH IS VERY BAD FOR PERFORMANCE!!!!
                                    nonAir++;
                                    current[I(ix, iy, iz)]  = stateMapper.getBaseId(light, state, biome);
                                }
                            }
                        }
                    }
                    if (nonAir != 0) {
                        {//Generate mipping
                            //Mip L1
                            int i = 0;
                            for (int y = 0; y < 4; y += 2) {
                                for (int z = 0; z < 4; z += 2) {
                                    for (int x = 0; x < 4; x += 2) {
                                        current[4 * 4 * 4 + i++] = Mipper.mip(
                                                current[I(x, y, z)], current[I(x+1, y, z)], current[I(x, y, z+1)], current[I(x+1, y, z+1)],
                                                current[I(x, y+1, z)], current[I(x+1, y+1, z)], current[I(x, y+1, z+1)], current[I(x+1, y+1, z+1)],
                                                stateMapper);
                                    }
                                }
                            }
                            //Mip L2
                            section[I(ox, oy, oz)] = Mipper.mip(
                                    current[J(0,0,0)], current[J(1,0,0)], current[J(0,0,1)], current[J(1,0,1)],
                                    current[J(0,1,0)], current[J(1,1,0)], current[J(0,1,1)], current[J(1,1,1)],
                                    stateMapper);
                        }

                        //Update existence mask
                        msk |= 1L<<I(ox, oy, oz);
                        subSections[I(ox, oy, oz)] = current;
                        current = new long[4*4*4+2*2*2+1];
                    }
                }
            }
        }

        {//Generate mipping
            //Mip L3
            int i = 0;
            for (int y = 0; y < 4; y+=2) {
                for (int z = 0; z < 4; z += 2) {
                    for (int x = 0; x < 4; x += 2) {
                        section[4 * 4 * 4 + i++] = Mipper.mip(section[I(x, y, z)],       section[I(x+1, y, z)],       section[I(x, y, z+1)],      section[I(x+1, y, z+1)],
                                                            section[I(x, y+1, z)], section[I(x+1, y+1, z)], section[I(x, y+1, z+1)], section[I(x+1, y+1, z+1)],
                                stateMapper);
                    }
                }
            }
            //Mip L4
            section[4*4*4+2*2*2] = Mipper.mip(section[J(0, 0, 0)], section[J(1, 0, 0)], section[J(0, 0, 1)], section[J(1, 0, 1)],
                    section[J(0, 1, 0)], section[J(1, 1, 0)], section[J(0, 1, 1)], section[J(1, 1, 1)],
                    stateMapper);
        }

        return new VoxelizedSection(section, msk, subSections, sx, sy, sz);
    }
}
