package me.cortex.voxy.client.core.rendering.building;


import me.cortex.voxy.client.core.util.Mesher2D;


public class QuadEncoder {

    public static int getX(long data) {
        return (int) ((data>>21)&0b11111);
    }
    public static int getY(long data) {
        return (int) ((data>>16)&0b11111);
    }
    public static int getZ(long data) {
        return (int) ((data>>11)&0b11111);
    }
    public static int getW(long data) {
        return (int) ((data>>3)&0b1111)+1;
    }
    public static int getH(long data) {
        return (int) ((data>>7)&0b1111)+1;
    }
    public static int getFace(long data) {
        return (int) (data&0b111);
    }

    //Note: the encodedMeshedData is from the Mesher2D
    public static int encodePosition(int face, int otherAxis, int encodedMeshedData) {
        if (false&&(Mesher2D.getW(encodedMeshedData) > 16 || Mesher2D.getH(encodedMeshedData) > 16)) {
            throw new IllegalStateException("Width or height > 16");
        }
        int dat = face;
        dat |= ((Mesher2D.getW(encodedMeshedData) - 1) << 7) |
                ((Mesher2D.getH(encodedMeshedData) - 1) << 3);

        if (face>>1 == 0) {
            return dat |
                   (Mesher2D.getX(encodedMeshedData) << 21) |
                   (otherAxis << 16) |
                   (Mesher2D.getZ(encodedMeshedData) << 11);
        }
        if (face>>1 == 1) {
            return dat |
                   (Mesher2D.getX(encodedMeshedData) << 21) |
                   (Mesher2D.getZ(encodedMeshedData) << 16) |
                   (otherAxis << 11);
        }
        return dat |
               (otherAxis << 21) |
               (Mesher2D.getX(encodedMeshedData) << 16) |
               (Mesher2D.getZ(encodedMeshedData) << 11);
    }
}
