package me.cortex.zenith.client.core.model;

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

    public static boolean isFullyCovered(ColourDepthTextureData texture) {
        for (int pixel : texture.colour()) {
            if (((pixel>>24)&0xFF) == 0) {
                return false;
            }
        }
        return true;
    }


    public static int computeDepth(ColourDepthTextureData texture, int mode) {
        final var colourData = texture.colour();
        final var depthData = texture.depth();
        for (int i = 0; i < colourData.length; i++) {
            if ((colourData[0]&0xFF)==0) {
                continue;
            }
            int depth = depthData[0]&0xffffff;
        }
        return 0;
    }
}
