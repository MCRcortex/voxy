package me.cortex.zenith.client.core.model;

import java.util.Map;

//Texturing utils to manipulate data from the model bakery
public class TextureUtils {
    //Returns a bitset of
    public static int computeColourData(ColourDepthTextureData texture) {
        final var colour = texture.colour();
        int bitset = 0b101;
        for (int i = 0; i < colour.length && bitset != 0b010; i++) {
            int pixel = colour[i];
            int alpha = (pixel>>24)&0xFF;
            bitset |= (alpha != 0 && alpha != 255)?2:0;//Test if the pixel is translucent (has alpha)
            bitset &= (alpha != 0)?3:7;// test if the pixel is not empty (assumes that if alpha is 0 it wasnt written to!!) FIXME: THIS MIGHT NOT BE CORRECT
            bitset &= alpha != 255?6:7;// test if the pixel is anything but solid, (occlusion culling stuff)
        }
        return bitset;
    }

    //Returns the number of non pixels not written to
    public static int getNonWrittenPixels(ColourDepthTextureData texture) {
        int count = 0;
        for (int pixel : texture.depth()) {
            count += (((pixel>>8)&0xFFFFFF) == 0xFFFFFF)?1:0;
        }
        return count;
    }

    public static boolean isSolid(ColourDepthTextureData texture) {
        for (int pixel : texture.colour()) {
            if (((pixel>>24)&0xFF) != 255) {
                return false;
            }
        }
        return true;
    }

    public static final int DEPTH_MODE_AVG = 1;
    public static final int DEPTH_MODE_MAX = 2;
    public static final int DEPTH_MODE_MIN = 3;

    //Computes depth info based on written pixel data
    public static float computeDepth(ColourDepthTextureData texture, int mode) {
        final var colourData = texture.colour();
        final var depthData = texture.depth();
        long a = 0;
        long b = 0;
        if (mode == DEPTH_MODE_MIN) {
            a = Long.MAX_VALUE;
        }
        if (mode == DEPTH_MODE_MAX) {
            a = Long.MIN_VALUE;
        }
        for (int i = 0; i < colourData.length; i++) {
            if ((colourData[i]&0xFF)==0) {
                continue;
            }
            int depth = depthData[i]>>>8;
            if (mode == DEPTH_MODE_AVG) {
                a++;
                b += depth;
            } else if (mode == DEPTH_MODE_MAX) {
                a = Math.max(a, depth);
            } else if (mode == DEPTH_MODE_MIN) {
                a = Math.min(a, depth);
            }
        }

        if (mode == DEPTH_MODE_AVG) {
            if (a == 0) {
                return -1;
            }
            return u2fdepth((int) (b/a));
        } else if (mode == DEPTH_MODE_MAX) {
            if (a == Long.MIN_VALUE) {
                return -1;
            }
            return u2fdepth((int) a);
        } else if (mode == DEPTH_MODE_MIN) {
            if (a == Long.MAX_VALUE) {
                return -1;
            }
            return u2fdepth((int) a);
        }
        throw new IllegalArgumentException();
    }

    private static float u2fdepth(int depth) {
        return (((float)depth)/(float)(1<<24));
    }
}
