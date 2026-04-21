package me.cortex.voxy.client.core.model;

public abstract class ModelQueries {
    public static boolean faceExists(long metadata, int face) {
        return ((metadata>>(8*face))&0xFF)!=0xFF;
    }

    public static boolean faceCanBeOccluded(long metadata, int face) {
        return ((metadata>>(8*face))&0b100)==0b100;
    }

    public static boolean faceOccludes(long metadata, int face) {
        return faceExists(metadata, face) && ((metadata>>(8*face))&0b1)==0b1;
    }

    public static boolean faceUsesSelfLighting(long metadata, int face) {
        return ((metadata>>(8*face))&0b1000) != 0;
    }

    public static boolean isDoubleSided(long metadata) {
        return ((metadata>>(8*6))&4) != 0;
    }

    public static long _isDoubleSided(long metadata) {
        return ((metadata>>(8*6+2))&1L);
    }

    public static boolean isTranslucent(long metadata) {
        return ((metadata>>(8*6))&2) != 0;
    }

    public static long _isTranslucent(long metadata) {
        return ((metadata>>(8*6+1))&1L);
    }

    public static boolean containsFluid(long metadata) {
        return ((metadata>>(8*6))&8) != 0;
    }

    public static long _containsFluid(long metadata) {
        return ((metadata>>(8*6+3))&1L);
    }

    public static boolean isFluid(long metadata) {
        return ((metadata>>(8*6))&16) != 0;
    }

    public static long _isFluid(long metadata) {
        return ((metadata>>(8*6+4))&1L);
    }

    public static boolean isBiomeColoured(long metadata) {
        return ((metadata>>(8*6))&1L) != 0;
    }

    public static long _isBiomeColoured(long metadata) {
        return ((metadata>>(8*6))&1L);
    }

    public static long _notIsBiomeColoured(long metadata) {
        return (((~metadata)>>(8*6))&1L);
    }

    //NOTE: this might need to be moved to per face
    public static boolean cullsSame(long metadata) {
        return ((metadata>>(8*6))&32) != 0;
    }

    public static boolean isFullyOpaque(long metadata) {
        return ((metadata>>(8*6))&64) != 0;
    }

    public static long _isFullyOpaque(long metadata) {
        return ((metadata>>(8*6+6))&1L);
    }

    public static long lightEmission(long meta) {
        return (meta>>(8*6+7))&0xFL;
    }
}
