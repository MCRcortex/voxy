package me.cortex.voxy.client.core.model;

import me.jellysquid.mods.sodium.client.util.color.ColorSRGB;
import net.minecraft.client.renderer.texture.MipmapGenerator;

import java.util.Arrays;

//Texturing utils to manipulate data from the model bakery
public class TextureUtils {
    //Returns the number of non pixels not written to
    public static int getWrittenPixelCount(ColourDepthTextureData texture, int checkMode) {
        int count = 0;
        for (int i = 0; i < texture.colour().length; i++) {
            count += wasPixelWritten(texture, checkMode, i) ? 1 : 0;
        }
        return count;
    }

    public static boolean isSolid(ColourDepthTextureData texture) {
        for (int pixel : texture.colour()) {
            if (((pixel >> 24) & 0xFF) != 255) {
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
            return (data.depth()[index] & 0xFF) != 0;
        } else if (mode == WRITE_CHECK_DEPTH) {
            return (data.depth()[index] >>> 8) != ((1 << 24) - 1);
        } else if (mode == WRITE_CHECK_ALPHA) {
            //TODO:FIXME: for some reason it has an alpha of 1 even if its ment to be 0
            return ((data.colour()[index] >>> 24) & 0xff) > 1;
        }
        throw new IllegalArgumentException();
    }


    //0: nothing written
    //1: none tinted
    //2: some tinted
    //3: all tinted
    public static int computeFaceTint(ColourDepthTextureData texture, int checkMode) {
        boolean allTinted = true;
        boolean someTinted = false;
        boolean wasWriten = false;

        final var colourData = texture.colour();
        final var depthData = texture.depth();
        for (int i = 0; i < colourData.length; i++) {
            if (!wasPixelWritten(texture, checkMode, i)) {
                continue;
            }
            if ((colourData[i] & 0xFFFFFF) == 0 || (colourData[i] >>> 24) == 0) {//If the pixel is fully black (or translucent)
                continue;
            }
            boolean pixelTinited = (depthData[i] & (1 << 7)) != 0;
            wasWriten |= true;
            allTinted &= pixelTinited;
            someTinted |= pixelTinited;

        }
        if (!wasWriten) {
            return 0;
        }
        return someTinted ? (allTinted ? 3 : 2) : 1;
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
            int depth = depthData[i] >>> 8;
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
            return u2fdepth((int) (b / a));
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
        float depthF = (float) ((double) depth / ((1 << 24) - 1));
        //https://registry.khronos.org/OpenGL-Refpages/gl4/html/glDepthRange.xhtml
        // due to this and the unsigned bullshit, believe the depth value needs to get multiplied by 2

        ////Shouldent be needed due to the compute bake copy
        //depthF *= 2;
        //if (depthF > 1.00001f) {//Basicly only happens when a model goes out of bounds (thing)
        //    //System.err.println("Warning: Depth greater than 1");
        //    depthF = 1.0f;
        //}
        return depthF;
    }


    public static long[] generateMask(ColourDepthTextureData data, int checkMode) {
        return generateMask(data, checkMode, new long[data.width()*data.height()/64]);
    }
    public static long[] generateMask(ColourDepthTextureData data, int checkMode, long[] outMsk) {
        Arrays.fill(outMsk, 0);
        int i = 0;
        for (int y = 0; y < data.height(); y++) {
            for (int x = 0; x < data.width(); x++) {
                if (wasPixelWritten(data, checkMode, i)) {
                    outMsk[i/64] |= 1L << (i&63);
                }
                i++;
            }
        }
        return outMsk;
    }


    //NOTE: data goes from bottom left to top right (x first then y)
    public static int[] computeBounds(ColourDepthTextureData data, int checkMode) {
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

        int maxX = data.width() - 1;
        maxXCheck:
        do {
            for (int y = data.height() - 1; y != -1; y--) {
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


        int maxY = data.height() - 1;
        maxYCheck:
        do {
            for (int x = data.width() - 1; x != -1; x--) {
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


    public static int mipColours(boolean darkend, int C00, int C01, int C10, int C11) {
        darkend = !darkend;//Invert to make it easier
        float r = 0.0f;
        float g = 0.0f;
        float b = 0.0f;
        float a = 0.0f;
        if (darkend || (C00 >>> 24) != 0) {
            r += ColorSRGB.srgbToLinear((C00 >> 0) & 0xFF);
            g += ColorSRGB.srgbToLinear((C00 >> 8) & 0xFF);
            b += ColorSRGB.srgbToLinear((C00 >> 16) & 0xFF);
            a += darkend ? (C00 >>> 24) : ColorSRGB.srgbToLinear(C00 >>> 24);
        }
        if (darkend || (C01 >>> 24) != 0) {
            r += ColorSRGB.srgbToLinear((C01 >> 0) & 0xFF);
            g += ColorSRGB.srgbToLinear((C01 >> 8) & 0xFF);
            b += ColorSRGB.srgbToLinear((C01 >> 16) & 0xFF);
            a += darkend ? (C01 >>> 24) : ColorSRGB.srgbToLinear(C01 >>> 24);
        }
        if (darkend || (C10 >>> 24) != 0) {
            r += ColorSRGB.srgbToLinear((C10 >> 0) & 0xFF);
            g += ColorSRGB.srgbToLinear((C10 >> 8) & 0xFF);
            b += ColorSRGB.srgbToLinear((C10 >> 16) & 0xFF);
            a += darkend ? (C10 >>> 24) : ColorSRGB.srgbToLinear(C10 >>> 24);
        }
        if (darkend || (C11 >>> 24) != 0) {
            r += ColorSRGB.srgbToLinear((C11 >> 0) & 0xFF);
            g += ColorSRGB.srgbToLinear((C11 >> 8) & 0xFF);
            b += ColorSRGB.srgbToLinear((C11 >> 16) & 0xFF);
            a += darkend ? (C11 >>> 24) : ColorSRGB.srgbToLinear(C11 >>> 24);
        }

        return ColorSRGB.linearToSrgb(
                r / 4,
                g / 4,
                b / 4,
                darkend ? ((int) a) / 4 : ARGB.linearToSrgbChannel(a / 4)
        );
    }
}