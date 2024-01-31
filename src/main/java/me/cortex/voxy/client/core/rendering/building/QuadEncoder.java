package me.cortex.voxy.client.core.rendering.building;


import me.cortex.voxy.client.core.util.Mesher2D;


public class QuadEncoder {

    //Note: the encodedMeshedData is from the Mesher2D
    public static int encodePosition(int face, int otherAxis, int encodedMeshedData) {
        if (Mesher2D.getW(encodedMeshedData) > 16 || Mesher2D.getH(encodedMeshedData) > 16) {
            throw new IllegalStateException("Width or height > 16");
        }
        int out = face;
        out |= switch (face >> 1) {
            case 0 ->
                    (Mesher2D.getX(encodedMeshedData) << 21) |
                            (otherAxis << 16) |
                            (Mesher2D.getZ(encodedMeshedData) << 11) |
                            ((Mesher2D.getW(encodedMeshedData)-1) << 7) |
                            ((Mesher2D.getH(encodedMeshedData)-1) << 3);

            case 1 ->
                    (Mesher2D.getX(encodedMeshedData) << 21) |
                            (Mesher2D.getZ(encodedMeshedData) << 16) |
                            (otherAxis << 11) |
                            ((Mesher2D.getW(encodedMeshedData)-1) << 7) |
                            ((Mesher2D.getH(encodedMeshedData)-1) << 3);

            case 2 ->
                    (otherAxis << 21) |
                            (Mesher2D.getX(encodedMeshedData) << 16) |
                            (Mesher2D.getZ(encodedMeshedData) << 11) |
                            ((Mesher2D.getW(encodedMeshedData)-1) << 7) |
                            ((Mesher2D.getH(encodedMeshedData)-1) << 3);
            default -> throw new IllegalStateException("Unexpected value: " + (face >> 1));
        };
        return out;
    }
}
