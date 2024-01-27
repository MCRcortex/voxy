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
            count += ((pixel&0xFF) == 0)?1:0;
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

    public static final int WRITE_CHECK_STENCIL = 1;
    public static final int WRITE_CHECK_DEPTH = 2;
    public static final int WRITE_CHECK_ALPHA = 3;
    private static boolean wasPixelWritten(ColourDepthTextureData data, int mode, int index) {
        if (mode == WRITE_CHECK_STENCIL) {
            return (data.depth()[index]&0xFF)!=0;
        } else if (mode == WRITE_CHECK_DEPTH) {
            return (data.depth()[index]>>>8)!=((1<<24)-1);
        } else if (mode == WRITE_CHECK_ALPHA) {
            return ((data.colour()[index]>>>24)&0xff)!=0;
        }
        throw new IllegalArgumentException();
    }

    public static final int DEPTH_MODE_AVG = 1;
    public static final int DEPTH_MODE_MAX = 2;
    public static final int DEPTH_MODE_MIN = 3;


    //Computes depth info based on written pixel data
    public static float computeDepth(ColourDepthTextureData texture, int mode, int checkMode) {
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
            if (!wasPixelWritten(texture, checkMode, i)) {
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
        float depthF = (float) ((double)depth/((1<<24)-1));
        //https://registry.khronos.org/OpenGL-Refpages/gl4/html/glDepthRange.xhtml
        // due to this and the unsigned bullshit, i believe the depth value needs to get multiplied by 2
        depthF *= 2;
        if (depthF > 1.00001f) {
            throw new IllegalArgumentException("Depth greater than 1");
        }
        return depthF;
    }


    //NOTE: data goes from bottom left to top right (x first then y)
    public static int[] computeBounds(ColourDepthTextureData data, int checkMode) {
        final var depth = data.depth();
        //Compute x bounds first
        int minX = 0;
        minXCheck:
        do {
            for (int y = 0; y < data.height(); y++) {
                int idx = minX + (y * data.width());
                if (wasPixelWritten(data, checkMode, idx)) {
                    break minXCheck;//pixel was written too so break from loop
                }
            }
            minX++;
        } while (minX != data.width());

        int maxX = data.width()-1;
        maxXCheck:
        do {
            for (int y = data.height()-1; y!=-1; y--) {
                int idx = maxX + (y * data.width());
                if (wasPixelWritten(data, checkMode, idx)) {
                    break maxXCheck;//pixel was written too so break from loop
                }
            }
            maxX--;
        } while (maxX != -1);
        maxX++;


        //Compute y bounds
        int minY = 0;
        minYCheck:
        do {
            for (int x = 0; x < data.width(); x++) {
                int idx = (minY * data.height()) + x;
                if (wasPixelWritten(data, checkMode, idx)) {
                    break minYCheck;//pixel was written too
                }
            }
            minY++;
        } while (minY != data.height());


        int maxY = data.height()-1;
        maxYCheck:
        do {
            for (int x = data.width()-1; x!=-1; x--) {
                int idx = (maxY * data.height()) + x;
                if (wasPixelWritten(data, checkMode, idx)) {
                    break maxYCheck;//pixel was written too so break from loop
                }
            }
            maxY--;
        } while (maxY != -1);
        maxY++;

        return new int[]{minX, maxX, minY, maxY};
    }
}
