package me.cortex.voxelmon.core.world.other;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.LocalRandom;
import net.minecraft.world.biome.BiomeKeys;

import java.util.List;

public class ColourResolver {
    //TODO: sample from multiple random values and avg it
    public static int[] resolveColour(BlockState state) {
        return resolveColour(state, 1234567890L);
    }

    //The way this works is it takes the and computes its colour, it then computes the area of the quad and the normal direction
    // it adds each area and colour to a per direcition colour
    // for non specific axis dimensions it takes the normal of each quad computes the dot between it and each of the directions
    // and averages that
    // if the colour doesnt exist for a specific axis set it to the average of the other axis and or make it translucent

    //TODO: fixme: finish
    public static int[] resolveColour(BlockState state, long randomValue) {
        if (state == Blocks.AIR.getDefaultState()) {
            return new int[6];
        }
        int[][] builder = new int[6][5];
        var random = new LocalRandom(randomValue);
        if (state.getFluidState().isEmpty()) {
            for (Direction direction : new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null}) {
                var quads = MinecraftClient.getInstance()
                        .getBakedModelManager()
                        .getBlockModels()
                        .getModel(state)
                        .getQuads(state, direction, random);
                for (var quad : quads) {
                    long weightColour = resolveQuadColour(quad);
                    int colour = (int) weightColour;
                    int weight = (int) (weightColour>>32);
                    if (direction == null) {
                        //TODO: apply normal multiplication to weight
                        for (int i = 0; i < 6; i++) {
                            builder[i][0] += weight;
                            builder[i][4] += weight * ((colour>>>24)&0xFF);
                            builder[i][3] += weight * ((colour>>>16)&0xFF);
                            builder[i][2] += weight * ((colour>>>8)&0xFF);
                            builder[i][1] += weight * ((colour>>>0)&0xFF);
                        }
                    } else {
                        builder[direction.getId()][0] += weight;
                        builder[direction.getId()][4] += weight*((colour>>>24)&0xFF);
                        builder[direction.getId()][3] += weight*((colour>>>16)&0xFF);
                        builder[direction.getId()][2] += weight*((colour>>>8)&0xFF);
                        builder[direction.getId()][1] += weight*((colour>>>0)&0xFF);
                    }
                }
            }
        } else {
            //TODO FIXME: need to account for both the fluid and block state at the same time
            //FIXME: make it not hacky and use the fluid handler thing from fabric

            long weightColour = resolveNI(MinecraftClient.getInstance().getBakedModelManager().getBlockModels().getModelParticleSprite(state).getContents().image);
            for (int i = 0; i < 6; i++) {
                builder[i][0] = 1;
                builder[i][1] += (weightColour>>0)&0xFF;
                builder[i][2] += (weightColour>>8)&0xFF;
                builder[i][3] += (weightColour>>16)&0xFF;
                builder[i][4] += (weightColour>>24)&0xFF;
            }
        }

        int[] out = new int[6];
        for (int i = 0; i < 6; i++) {
            int c = builder[i][0];
            if (c == 0) {
                continue;
            }
            int r = builder[i][4]/c;
            int g = builder[i][3]/c;
            int b = builder[i][2]/c;
            int a = builder[i][1]/c;
            out[i] = (r<<24)|(g<<16)|(b<<8)|a;
        }
        return out;
    }

    private static long resolveQuadColour(BakedQuad quad) {
        return resolveNI(quad.getSprite().getContents().image);
    }

    private static long resolveNI(NativeImage image) {
        int r = 0;
        int g = 0;
        int b = 0;
        int a = 0;
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int colour = image.getColor(x, y);
                if (((colour >>> 24)&0xFF) == 0) {
                    continue;
                }
                r += (colour >>> 0) & 0xFF;
                g += (colour >>> 8) & 0xFF;
                b += (colour >>> 16) & 0xFF;
                a += (colour >>> 24) & 0xFF;
                count++;
            }
        }
        if (count == 0) {
            return 0;
        }

        r /= count;
        g /= count;
        b /= count;
        a /= count;

        int colour = (r<<24)|(g<<16)|(b<<8)|a;

        return Integer.toUnsignedLong(colour)|(((long)count)<<32);
    }


    public static long resolveBiomeColour(String biomeId) {
        var biome = MinecraftClient.getInstance().world.getRegistryManager().get(RegistryKeys.BIOME).get(new Identifier(biomeId));
        int ARGBFoliage = biome.getFoliageColor();
        int ARGBWater = biome.getWaterColor();
        return Integer.toUnsignedLong(((ARGBFoliage&0xFFFFFF)<<8)|(ARGBFoliage>>>24)) | (Integer.toUnsignedLong(((ARGBWater&0xFFFFFF)<<8)|(ARGBWater>>>24))<<32);
    }
}
