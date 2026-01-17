package me.cortex.voxy.common.world.other;

import static me.cortex.voxy.common.world.other.Mapper.withLight;
import static me.cortex.voxy.common.world.other.Mapper.setMixed;

//Mipper for data
public class Mipper {
    //TODO: compute the opacity of the block then mip w.r.t those blocks
    // as distant horizons done


    //TODO: also pass in the level its mipping from, cause at lower levels you want to preserve block details
    // but at higher details you want more air



    //TODO: instead of opacity only, add a level to see if the visual bounding box allows for seeing through top down etc
    public static long mip(long I000, long I100, long I001, long I101,
                           long I010, long I110, long I011, long I111,
                          Mapper mapper) {
        //TODO: do a stable sort on all the entires, w.r.t the opacity and maybe light as a secondary???
        // then select the highest value
        // UPDATE, dumbass, the highest value _is_ the max/min



        int max = -1;

        // Count how many blocks are air vs non-air to detect mixed regions
        int airCount = 0;
        int solidCount = 0;

        //TODO: mip with respect to all the variables, what that means is take whatever has the highest count and return that
        //TODO: also average out the light level and set that as the new light level
        //For now just take the most top corner

        //TODO: i think it needs to compute the _max_ light level, since e.g. if a point is bright irl
        // you can see it from really really damn far away.
        // it could be a heavily weighted average with a huge preference to the top most lighting value
        if (!Mapper.isAir(I111)) {
            max = (mapper.getBlockStateOpacity(I111)<<4)|0b111;
            solidCount++;
        } else {
            airCount++;
        }
        if (!Mapper.isAir(I110)) {
            max = Math.max((mapper.getBlockStateOpacity(I110)<<4)|0b110, max);
            solidCount++;
        } else {
            airCount++;
        }
        if (!Mapper.isAir(I011)) {
            max = Math.max((mapper.getBlockStateOpacity(I011)<<4)|0b011, max);
            solidCount++;
        } else {
            airCount++;
        }
        if (!Mapper.isAir(I010)) {
            max = Math.max((mapper.getBlockStateOpacity(I010)<<4)|0b010, max);
            solidCount++;
        } else {
            airCount++;
        }
        if (!Mapper.isAir(I101)) {
            max = Math.max((mapper.getBlockStateOpacity(I101)<<4)|0b101, max);
            solidCount++;
        } else {
            airCount++;
        }
        if (!Mapper.isAir(I100)) {
            max = Math.max((mapper.getBlockStateOpacity(I100)<<4)|0b100, max);
            solidCount++;
        } else {
            airCount++;
        }
        if (!Mapper.isAir(I001)) {
            max = Math.max((mapper.getBlockStateOpacity(I001)<<4)|0b001, max);
            solidCount++;
        } else {
            airCount++;
        }
        if (!Mapper.isAir(I000)) {
            max = Math.max((mapper.getBlockStateOpacity(I000)<<4), max);
            solidCount++;
        } else {
            airCount++;
        }

        // Detect if this is a mixed region (contains both air and solid blocks)
        // If so, mark the result with the mixed flag so faces won't be incorrectly culled at LOD boundaries
        boolean isMixed = airCount > 0 && solidCount > 0;

        // CRITICAL: Also propagate the mixed flag from any input block that is already mixed.
        // This ensures the "boundary crust" information propagates up through ALL LOD levels,
        // not just LOD0→LOD1. Without this, LOD2+ sections would lose the mixed information
        // and incorrectly cull faces at LOD boundaries.
        isMixed |= Mapper.isMixed(I000) || Mapper.isMixed(I001) || Mapper.isMixed(I010) || Mapper.isMixed(I011) ||
                   Mapper.isMixed(I100) || Mapper.isMixed(I101) || Mapper.isMixed(I110) || Mapper.isMixed(I111);

        if (max != -1) {
            long result = switch (max&0b111) {
                case 0 -> I000;
                case 1 -> I001;
                case 2 -> I010;
                case 3 -> I011;
                case 4 -> I100;
                case 5 -> I101;
                case 6 -> I110;
                case 7 -> I111;
                default -> throw new IllegalStateException("Unexpected value: " + (max&0b111));
            };
            // Set the mixed flag if this mipped block came from a mixed air/solid region
            return isMixed ? setMixed(result) : result;
        } else {
            int blockLight = (Mapper.getLightId(I000) & 0xF0) + (Mapper.getLightId(I001) & 0xF0) + (Mapper.getLightId(I010) & 0xF0) + (Mapper.getLightId(I011) & 0xF0) +
                    (Mapper.getLightId(I100) & 0xF0) + (Mapper.getLightId(I101) & 0xF0) + (Mapper.getLightId(I110) & 0xF0) + (Mapper.getLightId(I111) & 0xF0);
            int skyLight = (Mapper.getLightId(I000) & 0x0F) + (Mapper.getLightId(I001) & 0x0F) + (Mapper.getLightId(I010) & 0x0F) + (Mapper.getLightId(I011) & 0x0F) +
                    (Mapper.getLightId(I100) & 0x0F) + (Mapper.getLightId(I101) & 0x0F) + (Mapper.getLightId(I110) & 0x0F) + (Mapper.getLightId(I111) & 0x0F);
            blockLight = blockLight / 8;
            skyLight = (int) Math.ceil((double) skyLight / 8);

            return withLight(I111, (blockLight << 4) | skyLight);
        }
    }
}
