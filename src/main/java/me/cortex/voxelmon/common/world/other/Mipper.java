package me.cortex.voxelmon.common.world.other;

//Mipper for data
public class Mipper {
    //TODO: also pass in the level its mipping from, cause at lower levels you want to preserve block details
    // but at higher details you want more air
    public static long mip(long I000, long I100, long I001, long I101,
                           long I010, long I110, long I011, long I111,
                          Mapper mapper) {
        //TODO: mip with respect to all the variables, what that means is take whatever has the highest count and return that
        //TODO: also average out the light level and set that as the new light level
        //For now just take the most top corner
        if (!Mapper.isAir(I111)) {
            return I111;
        }
        if (!Mapper.isAir(I110)) {
            return I110;
        }
        if (!Mapper.isAir(I011)) {
            return I011;
        }
        if (!Mapper.isAir(I010)) {
            return I010;
        }
        if (!Mapper.isAir(I101)) {
            return I101;
        }
        if (!Mapper.isAir(I100)) {
            return I100;
        }
        if (!Mapper.isAir(I001)) {
            return I001;
        }
        if (!Mapper.isAir(I000)) {
            return I000;
        }
        //TODO: need to account for different light levels of "air"
        return 0;
    }
}
