package me.cortex.voxelmon.core.world.other;

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
        if (I111 != 0) {
            return I111;
        }
        if (I110 != 0) {
            return I110;
        }
        if (I011 != 0) {
            return I011;
        }
        if (I010 != 0) {
            return I010;
        }
        if (I101 != 0) {
            return I101;
        }
        if (I100 != 0) {
            return I100;
        }
        if (I001 != 0) {
            return I001;
        }
        if (I000 != 0) {
            return I000;
        }
        return 0;
    }
}
