package me.cortex.zenith.client.core.model;

import java.util.Map;

//Texturing utils to manipulate data from the model bakery
public class TextureUtils {
    //Returns if any pixels are not fully transparent or fully translucent
    public static boolean hasAlpha(ColourDepthTextureData texture) {
        for (int pixel : texture.colour()) {
            int alpha = (pixel>>24)&0xFF;
            if (alpha != 0 && alpha != 255) {
                return true;
            }
        }
        return false;
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
    public static int computeDepth(ColourDepthTextureData texture, int mode) {
        final var colourData = texture.colour();
        final var depthData = texture.depth();
        long a = 0;
        long b = 0;
        if (mode == DEPTH_MODE_MIN) {
            a = Long.MAX_VALUE;
        }
        for (int i = 0; i < colourData.length; i++) {
            if ((colourData[0]&0xFF)==0) {
                continue;
            }
            int depth = depthData[0]>>>8;
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
            return (int) (b/a);
        } else if (mode == DEPTH_MODE_MAX || mode == DEPTH_MODE_MIN) {
            return (int) a;
        }
        throw new IllegalArgumentException();
    }
}
