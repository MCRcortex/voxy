package me.cortex.zenith.client.core.rendering.building;


import me.cortex.zenith.client.core.util.Mesher2D;
import me.cortex.zenith.common.world.other.Mapper;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;


public class QuadEncoder {
    private final BlockColors colourProvider;
    private final Mapper mapper;
    private final ClientWorld worldIn;
    public QuadEncoder(Mapper mapper, BlockColors colourProvider, ClientWorld worldIn) {
        this.colourProvider = colourProvider;
        this.mapper = mapper;
        this.worldIn = worldIn;
    }

    //Normalize block states that result in the same output state, (this improves meshing)
    // such as the 8 different types of leaves (due to distance from wood) should get normalized to 1 type
    // if the textures are the same
    public long normalizeBlockState(long id) {
        //TODO: This
        return id;
    }

    private int getColour(long id, int x, int y, int z) {
        //TODO: need to inject the biome somehow
        return this.colourProvider.getColor(this.mapper.getBlockStateFromId(id), this.worldIn, new BlockPos(x, y, z), 0);
    }

    //The way it works is that, when encoding, use a local colour mapping, then it remaps later to a global colour store
    public long encode(long id, int face, int otherAxis, int encodedMeshedData) {
        int encodePosition = encodePosition(face, otherAxis, encodedMeshedData);//26 bits
        int lighting = (int) ((id>>56)&0xFF);//8 bits

        int biome = (int) ((id>>47)&((1<<9)-1));
        int blockstate = (int) ((id>>20)&((1<<20)-1));

        //int blockColour = this.getColour(id, -1, -1, -1);
        // if blockColour is -1 it means it doesnt have colour

        return ((id>>>27)<<26)|Integer.toUnsignedLong(encodePosition);
    }




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
