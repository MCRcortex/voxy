package me.cortex.voxy.client.core.model;

import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class ARGB {
    private static final int LINEAR_CHANNEL_DEPTH = 1024;
    private static final short[] SRGB_TO_LINEAR = (short[]) Util.make(new short[256], (ss) -> {
        for(int i = 0; i < ss.length; ++i) {
            float f = (float)i / 255.0F;
            ss[i] = (short)Math.round(computeSrgbToLinear(f) * 1023.0F);
        }

    });
    private static final byte[] LINEAR_TO_SRGB = (byte[])Util.make(new byte[1024], (bs) -> {
        for(int i = 0; i < bs.length; ++i) {
            float f = (float)i / 1023.0F;
            bs[i] = (byte)Math.round(computeLinearToSrgb(f) * 255.0F);
        }

    });

    private static float computeSrgbToLinear(float f) {
        return f >= 0.04045F ? (float)Math.pow(((double)f + 0.055) / 1.055, 2.4) : f / 12.92F;
    }

    private static float computeLinearToSrgb(float f) {
        return f >= 0.0031308F ? (float)(1.055 * Math.pow((double)f, 0.4166666666666667) - 0.055) : 12.92F * f;
    }

    public static float srgbToLinearChannel(int i) {
        return (float)SRGB_TO_LINEAR[i] / 1023.0F;
    }

    public static int linearToSrgbChannel(float f) {
        return LINEAR_TO_SRGB[Mth.floor(f * 1023.0F)] & 255;
    }

    public static int meanLinear(int i, int j, int k, int l) {
        return color((alpha(i) + alpha(j) + alpha(k) + alpha(l)) / 4, linearChannelMean(red(i), red(j), red(k), red(l)), linearChannelMean(green(i), green(j), green(k), green(l)), linearChannelMean(blue(i), blue(j), blue(k), blue(l)));
    }

    private static int linearChannelMean(int i, int j, int k, int l) {
        int m = (SRGB_TO_LINEAR[i] + SRGB_TO_LINEAR[j] + SRGB_TO_LINEAR[k] + SRGB_TO_LINEAR[l]) / 4;
        return LINEAR_TO_SRGB[m] & 255;
    }

    public static int alpha(int i) {
        return i >>> 24;
    }

    public static int red(int i) {
        return i >> 16 & 255;
    }

    public static int green(int i) {
        return i >> 8 & 255;
    }

    public static int blue(int i) {
        return i & 255;
    }

    public static int color(int i, int j, int k, int l) {
        return (i & 255) << 24 | (j & 255) << 16 | (k & 255) << 8 | l & 255;
    }

    public static int color(int i, int j, int k) {
        return color(255, i, j, k);
    }

    public static int color(Vec3 vec3) {
        return color(as8BitChannel((float)vec3.x()), as8BitChannel((float)vec3.y()), as8BitChannel((float)vec3.z()));
    }

    public static int multiply(int i, int j) {
        if (i == -1) {
            return j;
        } else {
            return j == -1 ? i : color(alpha(i) * alpha(j) / 255, red(i) * red(j) / 255, green(i) * green(j) / 255, blue(i) * blue(j) / 255);
        }
    }

    public static int addRgb(int i, int j) {
        return color(alpha(i), Math.min(red(i) + red(j), 255), Math.min(green(i) + green(j), 255), Math.min(blue(i) + blue(j), 255));
    }

    public static int subtractRgb(int i, int j) {
        return color(alpha(i), Math.max(red(i) - red(j), 0), Math.max(green(i) - green(j), 0), Math.max(blue(i) - blue(j), 0));
    }

    public static int multiplyAlpha(int i, float f) {
        if (i != 0 && !(f <= 0.0F)) {
            return f >= 1.0F ? i : color(alphaFloat(i) * f, i);
        } else {
            return 0;
        }
    }

    public static int scaleRGB(int i, float f) {
        return scaleRGB(i, f, f, f);
    }

    public static int scaleRGB(int i, float f, float g, float h) {
        return color(alpha(i), ARGB.clamp((long)((int)((float)red(i) * f)), 0, 255), ARGB.clamp((long)((int)((float)green(i) * g)), 0, 255), ARGB.clamp((long)((int)((float)blue(i) * h)), 0, 255));
    }

    public static int scaleRGB(int i, int j) {
        return color(alpha(i), ARGB.clamp((long)red(i) * (long)j / 255L, 0, 255), ARGB.clamp((long)green(i) * (long)j / 255L, 0, 255), ARGB.clamp((long)blue(i) * (long)j / 255L, 0, 255));
    }

    public static int greyscale(int i) {
        int j = (int)((float)red(i) * 0.3F + (float)green(i) * 0.59F + (float)blue(i) * 0.11F);
        return color(alpha(i), j, j, j);
    }

    public static int clamp(long value, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException(min + " > " + max);
        }
        return (int) Math.min(max, Math.max(value, min));
    }

    public static int alphaBlend(int i, int j) {
        int k = alpha(i);
        int l = alpha(j);
        if (l == 255) {
            return j;
        } else if (l == 0) {
            return i;
        } else {
            int m = l + k * (255 - l) / 255;
            return color(m, alphaBlendChannel(m, l, red(i), red(j)), alphaBlendChannel(m, l, green(i), green(j)), alphaBlendChannel(m, l, blue(i), blue(j)));
        }
    }

    private static int alphaBlendChannel(int i, int j, int k, int l) {
        return (l * j + k * (i - j)) / i;
    }

    public static int srgbLerp(float f, int i, int j) {
        int k = Mth.lerpInt(f, alpha(i), alpha(j));
        int l = Mth.lerpInt(f, red(i), red(j));
        int m = Mth.lerpInt(f, green(i), green(j));
        int n = Mth.lerpInt(f, blue(i), blue(j));
        return color(k, l, m, n);
    }

    public static int linearLerp(float f, int i, int j) {
        return color(Mth.lerpInt(f, alpha(i), alpha(j)), LINEAR_TO_SRGB[Mth.lerpInt(f, SRGB_TO_LINEAR[red(i)], SRGB_TO_LINEAR[red(j)])] & 255, LINEAR_TO_SRGB[Mth.lerpInt(f, SRGB_TO_LINEAR[green(i)], SRGB_TO_LINEAR[green(j)])] & 255, LINEAR_TO_SRGB[Mth.lerpInt(f, SRGB_TO_LINEAR[blue(i)], SRGB_TO_LINEAR[blue(j)])] & 255);
    }

    public static int opaque(int i) {
        return i | -16777216;
    }

    public static int transparent(int i) {
        return i & 16777215;
    }

    public static int color(int i, int j) {
        return i << 24 | j & 16777215;
    }

    public static int color(float f, int i) {
        return as8BitChannel(f) << 24 | i & 16777215;
    }

    public static int white(float f) {
        return as8BitChannel(f) << 24 | 16777215;
    }

    public static int white(int i) {
        return i << 24 | 16777215;
    }

    public static int black(float f) {
        return as8BitChannel(f) << 24;
    }

    public static int black(int i) {
        return i << 24;
    }

    public static int colorFromFloat(float f, float g, float h, float i) {
        return color(as8BitChannel(f), as8BitChannel(g), as8BitChannel(h), as8BitChannel(i));
    }

    public static Vector3f vector3fFromRGB24(int i) {
        return new Vector3f(redFloat(i), greenFloat(i), blueFloat(i));
    }

    public static Vector4f vector4fFromARGB32(int i) {
        return new Vector4f(redFloat(i), greenFloat(i), blueFloat(i), alphaFloat(i));
    }

    public static int average(int i, int j) {
        return color((alpha(i) + alpha(j)) / 2, (red(i) + red(j)) / 2, (green(i) + green(j)) / 2, (blue(i) + blue(j)) / 2);
    }

    public static int as8BitChannel(float f) {
        return Mth.floor(f * 255.0F);
    }

    public static float alphaFloat(int i) {
        return from8BitChannel(alpha(i));
    }

    public static float redFloat(int i) {
        return from8BitChannel(red(i));
    }

    public static float greenFloat(int i) {
        return from8BitChannel(green(i));
    }

    public static float blueFloat(int i) {
        return from8BitChannel(blue(i));
    }

    private static float from8BitChannel(int i) {
        return (float)i / 255.0F;
    }

    public static int toABGR(int i) {
        return i & -16711936 | (i & 16711680) >> 16 | (i & 255) << 16;
    }

    public static int fromABGR(int i) {
        return toABGR(i);
    }

    public static int setBrightness(int i, float f) {
        int j = red(i);
        int k = green(i);
        int l = blue(i);
        int m = alpha(i);
        int n = Math.max(Math.max(j, k), l);
        int o = Math.min(Math.min(j, k), l);
        float g = (float)(n - o);
        float h;
        if (n != 0) {
            h = g / (float)n;
        } else {
            h = 0.0F;
        }

        float p;
        if (h == 0.0F) {
            p = 0.0F;
        } else {
            float q = (float)(n - j) / g;
            float r = (float)(n - k) / g;
            float s = (float)(n - l) / g;
            if (j == n) {
                p = s - r;
            } else if (k == n) {
                p = 2.0F + q - s;
            } else {
                p = 4.0F + r - q;
            }

            p /= 6.0F;
            if (p < 0.0F) {
                ++p;
            }
        }

        if (h == 0.0F) {
            j = k = l = Math.round(f * 255.0F);
            return color(m, j, k, l);
        } else {
            float q = (p - (float)Math.floor((double)p)) * 6.0F;
            float r = q - (float)Math.floor((double)q);
            float s = f * (1.0F - h);
            float t = f * (1.0F - h * r);
            float u = f * (1.0F - h * (1.0F - r));
            switch ((int)q) {
                case 0:
                    j = Math.round(f * 255.0F);
                    k = Math.round(u * 255.0F);
                    l = Math.round(s * 255.0F);
                    break;
                case 1:
                    j = Math.round(t * 255.0F);
                    k = Math.round(f * 255.0F);
                    l = Math.round(s * 255.0F);
                    break;
                case 2:
                    j = Math.round(s * 255.0F);
                    k = Math.round(f * 255.0F);
                    l = Math.round(u * 255.0F);
                    break;
                case 3:
                    j = Math.round(s * 255.0F);
                    k = Math.round(t * 255.0F);
                    l = Math.round(f * 255.0F);
                    break;
                case 4:
                    j = Math.round(u * 255.0F);
                    k = Math.round(s * 255.0F);
                    l = Math.round(f * 255.0F);
                    break;
                case 5:
                    j = Math.round(f * 255.0F);
                    k = Math.round(s * 255.0F);
                    l = Math.round(t * 255.0F);
            }

            return color(m, j, k, l);
        }
    }
}