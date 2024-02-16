package me.cortex.voxy.client.core.model;

import me.jellysquid.mods.sodium.client.util.color.ColorSRGB;
import net.minecraft.util.math.ColorHelper;

//Texturing utils to manipulate data from the model bakery
public class TextureUtils {
    //Returns the number of non pixels not written to
    public static int getWrittenPixelCount(ColourDepthTextureData texture, int checkMode) {
        int count = 0;
        for (int i = 0; i < texture.colour().length; i++) {
            count += wasPixelWritten(texture, checkMode, i)?1:0;
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
            //TODO:FIXME: for some reason it has an alpha of 1 even if its ment to be 0
            return ((data.colour()[index]>>>24)&0xff)>1;
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
            System.err.println("Warning: Depth greater than 1");
            depthF = 1.0f;
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
        //maxX++;


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
        //maxY++;

        return new int[]{minX, maxX, minY, maxY};
    }














    public static int mipColours(int one, int two, int three, int four) {
        return weightedAverageColor(weightedAverageColor(one, two), weightedAverageColor(three, four));
    }

    private static int weightedAverageColor(int one, int two) {
        int alphaOne = ColorHelper.Abgr.getAlpha(one);
        int alphaTwo = ColorHelper.Abgr.getAlpha(two);
        if (alphaOne == alphaTwo) {
            return averageRgb(one, two, alphaOne);
        } else if (alphaOne == 0) {
            return two & 16777215 | alphaTwo >> 2 << 24;
        } else if (alphaTwo == 0) {
            return one & 16777215 | alphaOne >> 2 << 24;
        } else {
            float scale = 1.0F / (float)(alphaOne + alphaTwo);
            float relativeWeightOne = (float)alphaOne * scale;
            float relativeWeightTwo = (float)alphaTwo * scale;
            float oneR = ColorSRGB.srgbToLinear(ColorHelper.Abgr.getRed(one)) * relativeWeightOne;
            float oneG = ColorSRGB.srgbToLinear(ColorHelper.Abgr.getGreen(one)) * relativeWeightOne;
            float oneB = ColorSRGB.srgbToLinear(ColorHelper.Abgr.getBlue(one)) * relativeWeightOne;
            float twoR = ColorSRGB.srgbToLinear(ColorHelper.Abgr.getRed(two)) * relativeWeightTwo;
            float twoG = ColorSRGB.srgbToLinear(ColorHelper.Abgr.getGreen(two)) * relativeWeightTwo;
            float twoB = ColorSRGB.srgbToLinear(ColorHelper.Abgr.getBlue(two)) * relativeWeightTwo;
            float linearR = oneR + twoR;
            float linearG = oneG + twoG;
            float linearB = oneB + twoB;
            int averageAlpha = alphaOne + alphaTwo >> 1;
            return ColorSRGB.linearToSrgb(linearR, linearG, linearB, averageAlpha);
        }
    }

    private static int averageRgb(int a, int b, int alpha) {
        float ar = ColorSRGB.srgbToLinear(ColorHelper.Abgr.getRed(a));
        float ag = ColorSRGB.srgbToLinear(ColorHelper.Abgr.getGreen(a));
        float ab = ColorSRGB.srgbToLinear(ColorHelper.Abgr.getBlue(a));
        float br = ColorSRGB.srgbToLinear(ColorHelper.Abgr.getRed(b));
        float bg = ColorSRGB.srgbToLinear(ColorHelper.Abgr.getGreen(b));
        float bb = ColorSRGB.srgbToLinear(ColorHelper.Abgr.getBlue(b));
        return ColorSRGB.linearToSrgb((ar + br) * 0.5F, (ag + bg) * 0.5F, (ab + bb) * 0.5F, alpha);
    }
}
