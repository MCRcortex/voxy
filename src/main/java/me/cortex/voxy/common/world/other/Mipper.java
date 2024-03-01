package me.cortex.voxy.common.world.other;

import static me.cortex.voxy.common.world.other.Mapper.withLight;

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

        //TODO: i think it needs to compute the _max_ light level, since e.g. if a point is bright irl
        // you can see it from really really damn far away.
        // it could be a heavily weighted average with a huge preference to the top most lighting value
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

        int blockLight = (Mapper.getLightId(I000)&0xF0)+(Mapper.getLightId(I001)&0xF0)+(Mapper.getLightId(I010)&0xF0)+(Mapper.getLightId(I011)&0xF0)+
                         (Mapper.getLightId(I100)&0xF0)+(Mapper.getLightId(I101)&0xF0)+(Mapper.getLightId(I110)&0xF0)+(Mapper.getLightId(I111)&0xF0);
        int skyLight   = (Mapper.getLightId(I000)&0x0F)+(Mapper.getLightId(I001)&0x0F)+(Mapper.getLightId(I010)&0x0F)+(Mapper.getLightId(I011)&0x0F)+
                         (Mapper.getLightId(I100)&0x0F)+(Mapper.getLightId(I101)&0x0F)+(Mapper.getLightId(I110)&0x0F)+(Mapper.getLightId(I111)&0x0F);
        blockLight = blockLight/8;
        skyLight = (int) Math.ceil((double)skyLight/8);

        return withLight(I111, (blockLight<<4)|skyLight);
    }
}
