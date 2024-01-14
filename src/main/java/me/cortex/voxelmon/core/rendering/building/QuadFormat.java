package me.cortex.voxelmon.core.rendering.building;


import me.cortex.voxelmon.core.util.Mesher2D;
import me.cortex.voxelmon.core.world.other.Mapper;

/*
8 - Light (can probably make it 3,3 bit lighting then i get 2 spare bits for other things)

8 - R
8 - G
8 - B
4 - A

5 - x
5 - y
5 - z
4 - w
4 - h
3 - face
 */


//TODO: might be able to fit it within 32 bits _very hackily_ (keep the same position data)
// but then have a per section LUT


//V2 QUAD FORMAT (enables animations to work)
/*
1  - spare
8  - light
9  - biome id
20 - block id
5 - x
5 - y
5 - z
4 - w
4 - h
3 - face
 */

public class QuadFormat {
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

    //TODO: finish
    public static long encode(Mapper mapper, long id, int encodedPosition) {
        return ((id>>>27)<<26)|Integer.toUnsignedLong(encodedPosition);
    }

    public static long encode(Mapper mapper, long id, int face, int otherAxis, int encodedMeshedData) {
        return encode(mapper, id, encodePosition(face, otherAxis, encodedMeshedData));
    }



    private static long encodeV2(Mapper mapper, long id, int face, int otherAxis, int encodedMeshedData) {
        int position = encodePosition(face, otherAxis, encodedMeshedData);
        int lighting = (int) ((id>>56)&0xFF);
        int biome = (int) ((id>>47)&((1<<9)-1));
        int blockstate = (int) ((id>>20)&((1<<20)-1));

        return -1;
    }
}
